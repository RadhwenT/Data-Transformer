# Modèle entité-association (MCD)

```mermaid
erDiagram
  DT_users ||--o{ DT_processes : creates
  DT_users ||--o{ DT_executions : launches
  DT_processes ||--o{ DT_executions : generates
  DT_processes ||--o{ DT_process_rules : uses
  DT_rules ||--o{ DT_process_rules : applied_by
  DT_executions ||--|{ DT_files : produces

  DT_users {
    int id PK
    string first_name
    string last_name
    string email "UNIQUE, NOT NULL"
    string password_hash "never in clear text - BCrypt"
    string role "ENUM: USER, ADMIN"
    boolean active "compte desactive si false (suppression logique)"
  }

  DT_processes {
    int id PK
    string name "UNIQUE per user"
    string description
    string input_format "ENUM: JSON, XML"
    string output_format "ENUM: JSON, XML"
    datetime created_at
    boolean active "traitement desactive si false (suppression logique, historique preserve)"
    int user_id FK
  }

  DT_rules {
    int id PK
    string name "reusable catalog, e.g. Uppercase"
    string type
    string description
  }

  DT_process_rules {
    int id PK
    int execution_order "UNIQUE per process_id"
    string source_field
    string source_field_2
    string target_field
    int process_id FK
    int rule_id FK
  }

  DT_executions {
    int id PK
    datetime started_at
    datetime finished_at "NULL while PENDING or RUNNING"
    string status "ENUM: PENDING, RUNNING, SUCCESS, FAILED"
    string error_message "NULL if SUCCESS"
    int process_id FK
    int user_id FK
  }

  DT_files {
    int id PK
    string name
    string path
    string type "ENUM: INPUT, OUTPUT"
    datetime created_at
    int execution_id FK
  }
```

## Note — rôles et suppression logique

`DT_users.role` distingue deux profils : `USER` (crée et exécute ses propres traitements) et `ADMIN` (gère les comptes et consulte une vue agrégée tous utilisateurs, mais n'a pas accès aux écrans de traitement/exécution). Un compte peut être désactivé (`active = false`) plutôt que supprimé, pour préserver l'historique des traitements et exécutions qu'il a créés — même logique de suppression logique appliquée à `DT_processes.active`.