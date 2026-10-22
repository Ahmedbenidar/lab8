package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;

import javax.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Réessaie une modification rejetée pour cause de version obsolète :
 * on recharge la dernière version puis on réapplique la modification demandée.
 */
public class ReservationRetryHandler {

    private final ReservationService service;
    private final int maxTentatives;
    private final long attenteInitialeMs;

    public ReservationRetryHandler(ReservationService service) {
        this(service, 3, 20L);
    }

    public ReservationRetryHandler(ReservationService service, int maxTentatives, long attenteInitialeMs) {
        this.service = service;
        this.maxTentatives = maxTentatives;
        this.attenteInitialeMs = attenteInitialeMs;
    }

    /**
     * @param reservation entité (potentiellement obsolète) portant les valeurs à enregistrer
     */
    public Reservation modifierAvecRetry(Reservation reservation) {
        if (reservation == null || reservation.getId() == null) {
            throw new IllegalArgumentException("La réservation doit être persistée (id requis)");
        }
        final Long id = reservation.getId();
        final String salle = reservation.getSalle();
        final LocalDateTime dateDebut = reservation.getDateDebut();
        final LocalDateTime dateFin = reservation.getDateFin();

        return executer(id, reservation, cible -> {
            cible.setSalle(salle);
            cible.setDateDebut(dateDebut);
            cible.setDateFin(dateFin);
        });
    }

    /**
     * Variante pour une modification partielle : le changement est réappliqué
     * sur la dernière version à chaque tentative.
     */
    public Reservation modifierAvecRetry(Long id, Consumer<Reservation> changement) {
        if (id == null) {
            throw new IllegalArgumentException("L'identifiant de la réservation est requis");
        }
        Reservation courante = recuperer(id);
        changement.accept(courante);
        return executer(id, courante, changement);
    }

    private Reservation executer(Long id, Reservation premierEssai, Consumer<Reservation> changement) {
        long attente = attenteInitialeMs;
        for (int tentative = 1; tentative <= maxTentatives; tentative++) {
            Reservation cible;
            if (tentative == 1) {
                cible = premierEssai;
            } else {
                cible = recuperer(id);
                changement.accept(cible);
            }
            try {
                Reservation resultat = service.modifierReservation(cible);
                System.out.println("Modification enregistrée à la tentative " + tentative
                        + " (version " + resultat.getVersion() + ").");
                return resultat;
            } catch (OptimisticLockException e) {
                System.out.println("Conflit détecté, tentative de retry " + tentative + "/" + maxTentatives);
                dormir(attente);
                attente *= 2;
            }
        }
        throw new OptimisticLockException(
                "Échec après " + maxTentatives + " tentatives : conflit persistant sur la réservation " + id);
    }

    private Reservation recuperer(Long id) {
        Reservation reservation = service.trouverParId(id);
        if (reservation == null) {
            throw new IllegalArgumentException("Réservation non trouvée : " + id);
        }
        return reservation;
    }

    private static void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrompu", e);
        }
    }
}
