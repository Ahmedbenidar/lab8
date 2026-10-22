# TP 6 — Conflit de réservation concurrent et optimistic locking (`@Version`)

Projet Maven complet correspondant au lab **« TP 6 : Simuler un conflit de réservation concurrent et démontrer l'optimistic locking avec @Version »** (Hibernate & JPA).

## Prérequis

- JDK 8+
- Maven 3.6+
- Aucun serveur de base de données : H2 en mémoire (`jdbc:h2:mem:testdb`)

## Structure

```
src/main/java/com/example
├── model/Reservation.java                 # entité avec @Version
├── service/ReservationService.java
├── service/ReservationServiceImpl.java    # CRUD + détection du conflit
├── ConcurrentReservationSimulator.java    # étapes 6 et 7 : conflit non géré
├── ReservationRetryHandler.java           # étape 8 : retry avec rechargement
└── ConcurrentReservationWithRetry.java    # étape 9 : conflit absorbé
src/main/resources/META-INF/persistence.xml
pom.xml
```

## Exécution

```bash
mvn clean compile

# Étapes 6-7 : le conflit est détecté et remonte en OptimisticLockException
mvn exec:java -Dexec.mainClass=com.example.ConcurrentReservationSimulator

# Étapes 8-9 : le conflit est absorbé par le retry
mvn exec:java -Dexec.mainClass=com.example.ConcurrentReservationWithRetry
```

> Sous **PowerShell**, les propriétés `-D...` doivent être entre guillemets :
> `mvn exec:java "-Dexec.mainClass=com.example.ConcurrentReservationSimulator"`

## Résultat attendu

![Simulation sans retry — conflit détecté](docs/images/01-conflit-detecte.png)

![Simulation avec retry — conflit résolu](docs/images/02-retry-resolu.png)

## Point clé : `merge` et non `find` + copie

Le code du lab propose un `modifierReservation` qui fait `em.find(...)` puis copie les champs :
cette version **ne déclenche jamais** `OptimisticLockException`, car `find` recharge la version
courante en base puis l'entité managée est mise à jour sans comparaison de version
(écrasement silencieux, *last write wins*).

Ici la mise à jour utilise `em.merge(reservation)` avec l'entité **détachée** : Hibernate compare
la version portée par l'entité à celle de la base et lève une erreur si elle a changé
(`StaleObjectStateException`, convertie en `OptimisticLockException`).

## Stratégie de retry

`ReservationRetryHandler` :

1. tente la modification avec l'entité fournie (version potentiellement obsolète) ;
2. en cas de `OptimisticLockException`, recharge la dernière version, réapplique la modification ;
3. réessaie avec un backoff exponentiel, puis échoue explicitement après `MAX_RETRIES`.

Une surcharge `modifierAvecRetry(Long id, Consumer<Reservation> changement)` permet de ne
réappliquer qu'une modification partielle sur la dernière version (cas recommandé en production).

## Bonnes pratiques

- `@Version` sur un `int`/`long` (ou `@Version` + `@Temporal` pour un timestamp).
- Ne jamais exposer ni modifier le champ `version` côté API : Hibernate le gère.
- Toujours recharger l'entité avant de réessayer ; ne pas réutiliser l'instance obsolète.
- Limiter le nombre de tentatives et informer l'utilisateur en cas d'échec.
- Pour des sections critiques (compteurs, stock), préférer un verrou pessimiste (`LockModeType.PESSIMISTIC_WRITE`).
