package main;


import java.util.ArrayList;
import java.util.List;

public class Proportion {

private static int movingWindows;

    private Proportion() {
    }


    public static void findProportion(List<Ticket> ticketList) {
        List<Ticket> injectedVersion = new ArrayList<>();
        findInjectedVersion(ticketList, injectedVersion);

        int total = ticketList.size();
        movingWindows = calculatemovingWindows(total);

        List<Ticket> newProportionTicket = new ArrayList<>();
        processTicketList(ticketList, injectedVersion, newProportionTicket);
    }

    private static void findInjectedVersion(List<Ticket> ticketList, List<Ticket> injectedVersion) {
        for (Ticket ticket : ticketList) {
            if (ticket.getOV().equals(ticket.getFV()) && ticket.getIV() == 0) {
                ticket.setIV(ticket.getFV());
                injectedVersion.add(ticket);
            }
        }
    }

    private static int calculatemovingWindows(int total) {
        return total / 100;
    }

    private static void processTicketList(List<Ticket> ticketList, List<Ticket> injectedVersion, List<Ticket> newProportionTicket) {
        for (Ticket ticket : ticketList) {
            if (!injectedVersion.contains(ticket)) {
                if (ticket.getIV() != 0) {
                	addTicketToMovingWindow(newProportionTicket, ticket);
                } else {
                    injectedProportion(newProportionTicket, ticket);
                }
            }
        }
    }

    public static void addTicketToMovingWindow(List<Ticket> movingWindow, Ticket ticket) {
        if (movingWindow.size() < movingWindows) {
            movingWindow.add(ticket); 
        } else {
            movingWindow.remove(0);
            movingWindow.add(ticket); 
        }
    }


    public static void injectedProportion(List<Ticket> newProportionTicket, Ticket ticket) {
        float pTotalSum = calculatePTotalSum(newProportionTicket);
        int avgPFloor = calculateAvgPFloor(pTotalSum);
        int predictedIv = calculatePredictedIv(ticket, avgPFloor);
        ticket.setIV(Math.min(predictedIv, ticket.getOV()));
    }

    private static float calculatePTotalSum(List<Ticket> newProportionTicket) {
        float pTotalSum = 0;
        for (Ticket t : newProportionTicket) {
            float p = calculatePFormula(t);
            pTotalSum += p;
        }
        return pTotalSum;
    }

    private static int calculateAvgPFloor(float pTotalSum) {
        return (int) Math.floor(pTotalSum / movingWindows);
    }

    private static int calculatePredictedIv(Ticket ticket, int avgPFloor) {
        int fv = ticket.getFV();
        int ov = ticket.getOV();
        return fv - (fv - ov) * avgPFloor;
    }


    public static void injectedProportion1(List<Ticket> newProportionTicket,Ticket ticket){
        float pTotalSum = calculatePTotalSum(newProportionTicket);
        int avgPFloor = calculateAverageIV(pTotalSum);
        int fv = ticket.getFV();
        int ov = ticket.getOV();
        int predictedIv = fv-(fv-ov)*avgPFloor;
        ticket.setIV(Math.min(predictedIv, ov));
    }


    private static int calculateAverageIV(float pTotalSum) {
        return (int)Math.floor(pTotalSum/movingWindows);
    }

    /**
     * Calcola la proporzione P per un Ticket.
     * @param ticket il Ticket per cui calcolare la proporzione
     * @return la proporzione P calcolata
     */
    private static float calculatePFormula(Ticket ticket) {
        final float fv = ticket.getFV();
        final float ov = ticket.getOV();
        final float iv = ticket.getIV();
        
        if (Float.compare(fv, ov) == 0) {
            return 0f;
        }
        
        return (fv - iv) / (fv - ov);
    }









}









