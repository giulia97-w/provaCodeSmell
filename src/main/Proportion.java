package main;

import java.util.ArrayList;
import java.util.List;

public class Proportion {
    private static final int WINDOW_SIZE_PERCENTAGE = 1;

    private Proportion() {
    }

    public static void proportion(List<Ticket> ticketList) {
        List<Ticket> trivialTickets = new ArrayList<>();
        for (Ticket ticket : ticketList) {
            if (isTrivialTicket(ticket)) {
                setTrivialTicketIv(ticket);
                trivialTickets.add(ticket);
            }
        }
        int numTickets = ticketList.size();
        int windowSize = calculateWindowSize(numTickets);
        List<Ticket> proportionTickets = new ArrayList<>();

        for (Ticket ticket : ticketList) {
            if (!trivialTickets.contains(ticket)) {
                if (ticket.getIV() != 0) {
                    addToProportionWindow(proportionTickets, ticket, windowSize);
                } else {
                    setIvUsingProportion(proportionTickets, ticket, windowSize);
                }
            }
        }
    }

    private static boolean isTrivialTicket(Ticket ticket) {
        return ticket.getOV().equals(ticket.getFV()) && ticket.getIV() == 0;
    }

    private static void setTrivialTicketIv(Ticket ticket) {
        ticket.setIV(ticket.getFV());
    }

    private static int calculateWindowSize(int numTickets) {
        return Math.max(numTickets * WINDOW_SIZE_PERCENTAGE / 100, 1);
    }

    private static void addToProportionWindow(List<Ticket> proportionTickets, Ticket ticket, int windowSize) {
        if (proportionTickets.size() < windowSize) {
            proportionTickets.add(ticket);
        } else {
            proportionTickets.remove(0);
            proportionTickets.add(ticket);
        }
    }

    private static void setIvUsingProportion(List<Ticket> proportionTickets, Ticket ticket, int windowSize) {
        float pTotalSum = 0;
        int startIndex = Math.max(proportionTickets.size() - windowSize, 0);
        List<Ticket> subList = proportionTickets.subList(startIndex, proportionTickets.size());
        for (Ticket t : subList) {
            pTotalSum += calculateProportion(t);
        }
        int avgPFloor = (int) Math.floor(pTotalSum / windowSize);
        int fv = ticket.getFV();
        int ov = ticket.getOV();
        int predictedIv = fv - (fv - ov) * avgPFloor;
        ticket.setIV(Math.min(predictedIv, ov));
    }

    private static int calculateProportion(Ticket ticket) {
        float fv = ticket.getFV();
        float ov = ticket.getOV();
        float iv = ticket.getIV();

        if (fv == ov) {
            return 0;
        }

        return (int) Math.floor((fv - iv) / (fv - ov));
    }
}
