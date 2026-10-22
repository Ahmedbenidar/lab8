package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;
import com.example.service.ReservationServiceImpl;

import javax.persistence.OptimisticLockException;
import java.time.LocalDateTime;

/**
 * Simule deux utilisateurs qui chargent la même réservation puis la modifient :
 * la seconde modification porte sur une version obsolète et est donc rejetée.
 */
public class ConcurrentReservationSimulator {

    public static void main(String[] args) {
        ReservationService service = new ReservationServiceImpl();
        try {
            LocalDateTime debut = LocalDateTime.now().plusDays(1).withNano(0);

            Reservation reservation = new Reservation("Salle A", debut, debut.plusHours(2));
            reservation = service.creerReservation(reservation);
            System.out.println("Réservation initiale : " + reservation);

            // Deux "transactions" chargent la même ligne (même version en base)
            Reservation tx1 = service.trouverParId(reservation.getId());
            Reservation tx2 = service.trouverParId(reservation.getId());
            System.out.println("Version chargée par tx1 : " + tx1.getVersion()
                    + " | version chargée par tx2 : " + tx2.getVersion());

            tx1.setSalle("Salle B");
            service.modifierReservation(tx1);
            System.out.println("Transaction 1 : modification réussie (version "
                    + service.trouverParId(reservation.getId()).getVersion() + ").");

            tx2.setSalle("Salle C");
            try {
                service.modifierReservation(tx2);
                System.out.println("Transaction 2 : modification réussie.");
            } catch (OptimisticLockException e) {
                System.err.println("Transaction 2 : conflit détecté - " + e.getMessage());
            }

            System.out.println("État final en base : " + service.trouverParId(reservation.getId()));
        } finally {
            ReservationServiceImpl.fermer();
        }
    }
}
