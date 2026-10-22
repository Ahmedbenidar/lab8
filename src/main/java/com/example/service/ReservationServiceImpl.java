package com.example.service;

import com.example.model.Reservation;
import org.hibernate.StaleStateException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;
import java.util.List;

public class ReservationServiceImpl implements ReservationService {

    private static volatile EntityManagerFactory entityManagerFactory;

    private static EntityManagerFactory emf() {
        if (entityManagerFactory == null) {
            synchronized (ReservationServiceImpl.class) {
                if (entityManagerFactory == null) {
                    entityManagerFactory = Persistence.createEntityManagerFactory("reservationPU");
                }
            }
        }
        return entityManagerFactory;
    }

    public static void fermer() {
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
            entityManagerFactory = null;
        }
    }

    @Override
    public Reservation creerReservation(Reservation reservation) {
        EntityManager em = emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(reservation);
            tx.commit();
            return reservation;
        } catch (RuntimeException e) {
            rollback(tx);
            throw e;
        } finally {
            em.close();
        }
    }

    /**
     * Met à jour la réservation en comparant la version de l'entité détachée
     * à celle stockée en base : c'est ce contrôle qui déclenche l'optimistic locking.
     */
    @Override
    public Reservation modifierReservation(Reservation reservation) {
        if (reservation == null || reservation.getId() == null) {
            throw new IllegalArgumentException("La réservation doit être persistée avant d'être modifiée");
        }
        EntityManager em = emf().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (em.find(Reservation.class, reservation.getId()) == null) {
                throw new IllegalArgumentException("Réservation non trouvée : " + reservation.getId());
            }
            Reservation managed = em.merge(reservation);
            tx.commit();
            return managed;
        } catch (RuntimeException e) {
            rollback(tx);
            OptimisticLockException conflit = conflitOptimiste(e);
            if (conflit != null) {
                throw conflit;
            }
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public Reservation trouverParId(Long id) {
        EntityManager em = emf().createEntityManager();
        try {
            return em.find(Reservation.class, id);
        } finally {
            em.close();
        }
    }

    @Override
    public List<Reservation> listerReservations() {
        EntityManager em = emf().createEntityManager();
        try {
            return em.createQuery("SELECT r FROM Reservation r ORDER BY r.id", Reservation.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    private static void rollback(EntityTransaction tx) {
        if (tx.isActive()) {
            tx.rollback();
        }
    }

    /**
     * Hibernate enveloppe parfois le conflit dans une RollbackException :
     * on remonte la chaîne de causes pour toujours exposer une OptimisticLockException.
     */
    private static OptimisticLockException conflitOptimiste(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException || cause instanceof StaleStateException) {
                return new OptimisticLockException(
                        "Version obsolète : la réservation a été modifiée par une transaction concurrente", e);
            }
        }
        return null;
    }
}
