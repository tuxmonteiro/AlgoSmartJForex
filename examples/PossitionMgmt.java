package jforex.strategies;

import com.dukascopy.api.*;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import java.util.List;

public class PossitionMgmt implements IStrategy {

    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IIndicators indicators;
    private int counter = 0;
    private IOrder order;
    
    @Configurable("Instrument")
    public Instrument instrument = Instrument.EURUSD;
    @Configurable("Take profit in pips")
    public int takeProfitPips = 15;
    @Configurable("Stop loss in pips")
    public int stopLossPips = 15;
        

    @Override
    public void onStart(IContext context) throws JFException {
        this.console = context.getConsole();
        this.indicators = context.getIndicators();
        this.history = context.getHistory();
        this.engine = context.getEngine();
    }
    
    
    public void onTick(Instrument instrument, ITick tick) throws JFException {
        if (instrument != this.instrument) {
            return;
        }
        
        List<IOrder> orders = engine.getOrders(instrument);
        double profitLossPips = 0;
        for(IOrder order : orders) {
            profitLossPips += order.getProfitLossInPips();
        }
        
        if(profitLossPips < -stopLossPips || profitLossPips > takeProfitPips) {
            for(IOrder order : engine.getOrders(instrument)) {
                if(!order.getState().equals(IOrder.State.CREATED)) {
                    order.close();
                }
            }
        }
    }
    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
    }
    
    public void onMessage(IMessage message) throws JFException {
    }
    
    public void onAccount(IAccount account) throws JFException {
    }

    public void onStop() throws JFException {
    }
    
    private String getLabel(Instrument instrument) {
        String label = instrument.name();
        label = label + (counter++);
        label = label.toUpperCase();
        return label;
    }
    
    private void closeOrder(IOrder order) throws JFException {
        if (order != null && isActive(order)) {
            order.close();
        }
    }

    private boolean isActive(IOrder order) throws JFException {
        if (order != null && order.getState() != IOrder.State.CLOSED && order.getState() != IOrder.State.CREATED && order.getState() != IOrder.State.CANCELED) {
            return true;
        }
        return false;
    }
       
}
