package com.example.sporttracking.api;

import com.example.sporttracking.progress.ProgressEntryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;

@RestController
@Validated
@RequestMapping("/api/users/{userId}/progress-summary")
public class ProgressSummaryController {
    private final ProgressEntryRepository repository;
    private final ObjectMapper mapper;
    private final String model;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final Semaphore slot = new Semaphore(1);

    public ProgressSummaryController(ProgressEntryRepository repository, ObjectMapper mapper,
                                    @Value("${app.ai.model:llama3.2:3b}") String model) {
        this.repository = repository;
        this.mapper = mapper;
        this.model = model;
    }

    public record Summary(String summary, String model, int measurementCount) {}

    @PostMapping
    public Summary summarize(@PathVariable @Positive Long userId) {
        var entries = repository.findTop30ByUserIdOrderByMeasurementDateDescIdDesc(userId);
        if (entries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ajoutez au moins une mesure avant de demander un résumé.");
        }
        if (!slot.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Une analyse est déjà en cours. Réessayez dans un instant.");
        }
        try {
            var data = entries.stream().map(e -> Map.of(
                    "date", e.getMeasurementDate().toString(),
                    "poidsKg", String.valueOf(e.getWeight()),
                    "tailleCm", String.valueOf(e.getWaistCircumference()),
                    "sommeilHeures", String.valueOf(e.getSleepHours()),
                    "energieSur5", String.valueOf(e.getEnergyLevel()))).toList();
            String prompt = """
                    Rédige en français un bref résumé descriptif des mesures suivantes.
                    Elles sont triées de la plus récente à la plus ancienne.
                    null signifie absent, jamais zéro. N'invente aucune valeur ni tendance.
                    Avec une seule mesure, précise que l'évolution ne peut pas être évaluée.
                    Ne donne aucun diagnostic, prescription, objectif de poids ou conseil médical.
                    Ne recommande ni restriction alimentaire ni augmentation d'entraînement.
                    Il y a exactement %d mesure(s). Ne crée aucune autre date ou mesure.
                    Maximum 120 mots, texte simple. Termine en rappelant que ce résumé IA
                    peut contenir des erreurs. Données :
                    """.formatted(entries.size()) + mapper.writeValueAsString(data);
            String body = mapper.writeValueAsString(Map.of("model", model,
                    "prompt", prompt, "stream", false,
                    "options", Map.of("temperature", 0, "num_predict", 300, "num_ctx", 4096)));
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:11434/api/generate"))
                    .timeout(Duration.ofSeconds(120)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw unavailable();
            var json = mapper.readTree(response.body());
            if (json == null || !json.isObject()) throw unavailable();
            String text = json.path("response").asText("").strip();
            if (text.isEmpty() || !json.path("done").asBoolean(false)
                    || "length".equals(json.path("done_reason").asText())) {
                throw unavailable();
            }
            // Reject generated dates not present in the source measurements.
            var dates = java.util.regex.Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b").matcher(text);
            var sourceDates = entries.stream().map(e -> e.getMeasurementDate().toString()).toList();
            while (dates.find()) {
                if (!sourceDates.contains(dates.group())) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Le résumé contient une date incohérente et a été écarté. Réessayez.");
                }
            }
            if (entries.size() == 1) {
                text = "Une seule mesure est disponible : elle ne permet pas d'évaluer une évolution.\n\n" + text;
            }
            return new Summary(text, model, entries.size());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (java.io.IOException ex) {
            throw unavailable();
        } finally {
            slot.release();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Ollama ne répond pas ou le modèle est indisponible. Vérifiez Ollama puis réessayez.");
    }
}
