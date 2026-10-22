package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;
import com.example.service.ReservationServiceImpl;

import java.time.LocalDateTime;

/**
 * Même scénario que {@link ConcurrentReservationSimulator}, mais la seconde
 * transaction passe par {@link ReservationRetryHandler} : le conflit est
 * absorbé, la modification est réappliquée sur la dernière version.
 */
public class ConcurrentReservationWithRetry {

    public static void main(String[] args) {
        ReservationService service = new ReservationServiceImpl();
        ReservationRetryHandler retryHandler = new ReservationRetryHandler(service);
        try {
            LocalDateTime debut = LocalDateTime.now().plusDays(1).withNano(0);

            Reservation reservation = new Reservation("Salle A", debut, debut.plusHours(2));
            reservation = service.creerReservation(reservation);
            System.out.println("Réservation initiale : " + reservation);

            Reservation tx1 = service.trouverParId(reservation.getId());
            Reservation tx2 = service.trouverParId(reservation.getId());

            tx1.setSalle("Salle B");
            service.modifierReservation(tx1);
            System.out.println("Transaction 1 : modification réussie.");

            tx2.setSalle("Salle C");
            try {
                Reservation resultat = retryHandler.modifierAvecRetry(tx2);
                System.out.println("Transaction 2 : modification réussie après retry -> " + resultat);
            } catch (RuntimeException e) {
                System.err.println("Transaction 2 : échec après retries - " + e.getMessage());
            }

            System.out.println("État final en base : " + service.trouverParId(reservation.getId()));
        } finally {
            ReservationServiceImpl.fermer();
        }
    }
}
