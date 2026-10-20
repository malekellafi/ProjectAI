## Ajout Ollama — 20 octobre 2026

La page `/progression` propose **Résumer ma progression**. L'appel
`POST /api/users/{userId}/progress-summary` lit au maximum les 30 dernières mesures,
sans enregistrer de résumé ni modifier les données. Les identifiants utilisateur
et les identifiants des lignes ne sont pas envoyés au modèle.

Le backend contacte uniquement `http://127.0.0.1:11434/api/generate`. Le modèle par
défaut est `llama3.2:3b`, déjà présent sur le poste de développement. Démarrer Ollama
avant de demander un résumé. Sur un autre poste, installer Ollama depuis son site
officiel et télécharger ce modèle avant utilisation. `APP_AI_MODEL` permet de
sélectionner un autre modèle local déjà installé ; aucune clé API n'est nécessaire.

Une seule génération peut être exécutée à la fois dans cette instance du backend.
Le délai maximal de réponse est de 120 secondes. Les dates générées absentes des
mesures sont rejetées ; cette protection ne garantit pas l'exactitude du texte.
Le résumé est descriptif, peut contenir des erreurs et ne remplace pas un avis
médical. Une mesure isolée ne permet pas d'évaluer une évolution.

Réponses : `200` avec `summary`, `model`, `measurementCount` ; `400` en l'absence de
mesure ; `429` si une analyse est déjà en cours ; `503` si Ollama échoue ou si la
réponse est rejetée. L'interface affiche le chargement, les erreurs et le résultat
en texte, et invalide le résultat lors du changement d'utilisateur ou du
rechargement des mesures.

Vérifications réalisées : compilation Maven avec JDK 21/cible 17 et compilation
Angular réussies, démarrage Spring Boot avec connexion MariaDB et validation
Hibernate, réponse `400` sans mesure puis réponse réelle `200` du modèle
`llama3.2:3b` avec une mesure fictive. Cette mesure a été supprimée après contrôle.
Aucun test automatisé, aucune migration et aucune réparation XAMPP.
La vérification visuelle du nouveau bouton reste à faire : l'outil navigateur
a refusé l'accès à la page par sa politique de sécurité pendant cette session.
Les chemins d'erreur `429` et `503` sont implémentés, mais n'ont pas été vérifiés
de bout en bout pendant cette session.

## Graphiques et résumés — 20 octobre 2026

La page progression affiche une courbe sélectionnable : poids, tour de taille,
sommeil ou énergie. Les dates déterminent l'espacement horizontal ; les valeurs
manquantes sont ignorées, et l'échelle verticale est explicitement ajustée aux
valeurs. La dernière valeur et la variation entre première et dernière date sont
calculées par Angular, sans IA. Une date unique ne donne pas de tendance.
Le backend rejette les réponses Ollama tronquées et ajoute systématiquement une
limite d'interprétation lorsqu'une seule mesure est disponible.
Compilations backend et frontend réussies sans tests automatisés. Contrôle visuel
du graphique restant à effectuer ; les réponses HTTP frontend/backend sont 200.
