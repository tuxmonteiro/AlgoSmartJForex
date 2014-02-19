package jforex;

import java.util.*;

import com.dukascopy.api.*;

public class trailingStop implements IStrategy {
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    
    
    @Configurable(value = "Instrument")
    public Instrument pInstrument = Instrument.EURUSD;
    @Configurable(value = "Trailing Stop, pips", stepSize = 0.1)
    public double tStop = 15.0;
    @Configurable(value = "Trailing Step, pips", stepSize = 0.1)
    public double tStep = 1.0;
    @Configurable(value = "Position Label")
    public String positionLabel = "";
    
    
    private String strategyName = this.getClass().getSimpleName();
    
    
    private void printMe(Object toPrint) throws JFException {
        console.getOut().println(pInstrument.name() + "|| " + toPrint.toString());
    }
    
    private double point() throws JFException {
        return pInstrument.getPipValue();
    }
    
    private void trailPosition(IOrder order, ITick tick) throws JFException {
        if (order != null && order.getState().equals(IOrder.State.FILLED)) {
            if (order.isLong()) {
                double newSL = tick.getBid() - tStop*point();
                if (tick.getBid() > order.getOpenPrice() + tStop*point() && newSL >= order.getStopLossPrice() + tStep*point()) {
                    printMe("Trailing Stop for LONG position: " + order.getLabel() + "; open price = " + order.getOpenPrice() + "; old SL = " + order.getStopLossPrice() + "; new SL = " + Double.toString(tick.getBid() - tStop*point()));
                    order.setStopLossPrice(newSL);
                    order.waitForUpdate(2000);
                }
            } else {
                double newSL = tick.getAsk() + tStop*point();
                if (tick.getAsk() < order.getOpenPrice() - tStop*point() && (newSL <= order.getStopLossPrice() - tStep*point() || order.getStopLossPrice() == 0.0)) {
                    printMe("Trailing Stop for SHORT position: " + order.getLabel() + "; open price = " + order.getOpenPrice() + "; old SL = " + order.getStopLossPrice() + "; new SL = " + Double.toString(tick.getAsk() + tStop*point()));
                    order.setStopLossPrice(newSL);
                    order.waitForUpdate(2000);
                }
            }
        }
    }
    
    @Override
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        
        Set<Instrument> instruments = new HashSet<Instrument>();
        instruments.add(pInstrument);
        context.setSubscribedInstruments(instruments, true);
        
        printMe("Strategy " + strategyName + " is started");
        if (positionLabel.length() == 0) {
            printMe("Position Label parameter not defined. Trailing stop for all positions");
        }
    }

    @Override
    public void onAccount(IAccount account) throws JFException {
    }

    @Override
    public void onMessage(IMessage message) throws JFException {
    }

    @Override
    public void onStop() throws JFException {
        printMe("Strategy " + strategyName + " is stopped");
    }

    @Override
    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (!instrument.equals(pInstrument)) {
            return;
        }
        if (positionLabel.length() == 0) {
            for (IOrder o : engine.getOrders(pInstrument)) {
                if (o.getState().equals(IOrder.State.FILLED)) {
                    trailPosition(o, tick);
                }
            }
        } else {
            IOrder order = engine.getOrder(positionLabel);
            if (order != null && order.getState().equals(IOrder.State.FILLED)) {
                trailPosition(order, tick);
            }
        }
    }
    
    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
    }
}