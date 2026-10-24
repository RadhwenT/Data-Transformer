# Diagramme de séquence — Scénario B : Exécution d'un traitement existant

```mermaid
sequenceDiagram
  actor U as Utilisateur
  participant A as Angular (Interface)
  participant S as Spring Boot (API)
  participant D as PostgreSQL

  U->>A: Recherche "Nettoyage clients" dans la liste
  U->>A: Clique "Utiliser"
  A-->>U: Affiche la zone de depot de fichier

  U->>A: Fournit clients.json (glisser-depose ou parcourir)
  A-->>U: Affiche apercu, boutons "Lancer" / "Annuler"

  opt Utilisateur clique "Annuler" (avant lancement)
    U->>A: Clique "Annuler"
    A-->>U: Efface la selection, retour a l'etat initial
    Note over A,D: Rien n'a ete envoye au serveur, aucune trace en base
  end

  U->>A: Clique "Lancer"
  A->>A: Desactive le bouton "Lancer", masque "Annuler"
  A->>S: POST /api/executions (process_id, fichier)
  S->>S: Verifie le format du fichier (JSON attendu)

  alt Format invalide
    S-->>A: 400 Bad Request (message erreur)
    A->>A: Reactive l'interface
    A-->>U: Affiche erreur "Format invalide, JSON attendu"
    Note over S,D: Aucune ligne creee dans DT_executions ni DT_files
  else Format valide
    S->>D: INSERT INTO DT_executions (status = PENDING)
    S->>D: INSERT INTO DT_files (type = INPUT)
    Note over S,D: Fichier d'entree toujours trace, quelle que soit l'issue
    S->>D: UPDATE DT_executions SET status = RUNNING
    A-->>U: Affiche barre de progression indeterminee (interface bloquee)

    S->>D: SELECT * FROM DT_process_rules WHERE process_id = ... ORDER BY execution_order
    D-->>S: Liste des regles ordonnees
    S->>S: Lit le fichier, applique les regles dans l'ordre
    Note over S: 1. Concatenate (prenom+nom -> nomComplet)<br/>2. Uppercase (nomComplet)<br/>3. Format date (date_naissance)

    alt Traitement reussi
      S->>S: Genere le fichier de sortie
      S->>D: INSERT INTO DT_files (type = OUTPUT)
      S->>D: UPDATE DT_executions SET status = SUCCESS
      S-->>A: 200 OK (status SUCCESS, lien resultat)
      A->>A: Reactive l'interface
      A-->>U: Affiche SUCCES + apercu / telecharger
    else Erreur pendant le traitement
      S->>D: UPDATE DT_executions SET status = FAILED, error_message
      Note over D: DT_files contient deja le fichier INPUT -> cardinalite 1..* respectee
      S-->>A: 200 OK (status FAILED, message)
      A->>A: Reactive l'interface
      A-->>U: Affiche message d'erreur
    end
  end
```

## Note sur la cardinalité `Execution` ↔ `File`

Le modèle de données impose qu'une exécution ait **au moins un fichier** (`1..*`). Pour que cette contrainte reste vraie dans tous les cas, le fichier d'entrée (`type = INPUT`) est systématiquement enregistré **dès que le format est validé**, avant même que le traitement ne démarre — indépendamment de l'issue finale (`SUCCESS` ou `FAILED`).

Le fichier de sortie (`type = OUTPUT`) n'est ajouté qu'en cas de succès.