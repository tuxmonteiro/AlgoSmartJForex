package jforex.wwwJForexStrategycom;

import java.util.*;
import java.math.*;
import java.text.*;  // DateFormat etc.
import java.awt.*;
import java.awt.Color;
import java.awt.geom.AffineTransform;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.io.*;




import com.dukascopy.api.*;



public class TURBO_trading_GBPUSD10min implements IStrategy {


     @Configurable("Instrument")
     public Instrument _currentInstrument = Instrument.GBPUSD;

     @Configurable("Period")
     public Period _currentPeriod = Period.TEN_MINS;
     
     public OfferSide _indOfferside = OfferSide.BID;


    @Configurable("SignalPeriod")
     public int _TimePeriod = 30;
     
    //@Configurable("Remove objects on stop")
    public int remove_objects=0;

        
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;
    private IChart userChart;
    private IAccount account;


    public int _objects_counter=0;

  
    public     int daynumber=0;

    public int _day;
    public int _hours;
    public int _minutes;

    
    @Configurable("Lots in %")
    public double lots = 25;
    
    //@Configurable("Max BUY Positions")
    public int _maxpos = 1;
 
    //@Configurable("Max SELL Positions")
    public int _maxpos_sell = 1;


    @Configurable("StopLoss")
    public int _StopLoss=77;
    
    @Configurable("TakeProfit")
    public int _TakeProfit=134;
         
    
    @Configurable("Trailing")
    public int _trailing=34;    
     
    //@Configurable("Breakeven")
    public int _breakeven=0;    
    
    //@Configurable("Breakeven pips")
    public int _breakeven_pips=0;  
    
        
//    @Configurable("StopLoss SELL")
    public int _StopLoss_sell=0;
    
//    @Configurable("TakeProfit SELL")
    public int _TakeProfit_sell=0;        
    
    public int _martingale_mul=2;

    @Configurable("Use Hour Trade")
    public int _UseHourTrade=0;

    @Configurable("From Hour Trade")
    public int _FromHourTrade=6;

    @Configurable("To Hour Trade")
    public int _ToHourTrade=18;          


    private int _sell_flag=0;
    private int _buy_flag=0;
    
    private int _order_fill_rejected;
    private int _order_submit_rejected;
    private int _order_submit_ok;
    private int _order_fill_ok;
    private String _order_label;
    private IOrder _message_order;
    
    private int _is_close_buy_condition;
    private int _is_close_sell_condition;
    private int _is_open_buy_condition;
    private int _is_open_sell_condition;

    public int _buy_at_the_start_flag=0;
    public int _sell_at_the_start_flag=0;
    private long _order_buy_timestamp=0;
    private long _order_sell_timestamp=0;    


    private IContext _context = null;
    private IEngine _engine = null;
    private IChart _chart = null;
    private IIndicators _indicators = null;
    private IHistory _history = null;
    private IConsole _console = null;
    private boolean _posCreated = false;
    private int _tagCounter = 0;
    public long _lastBarTime = 0;
    

    // Vars for strategy  
    private double _bar_CLOSE=0.0;
    private double _bar_CLOSE_sell=0.0;
    private double _tick_ASK=0.0;
    private double _tick_BID=0.0;
    public long _signalBarTime = 0;
    private int _make_deal=0;
    private int _make_deal_sell=0;
    public long _start_time=0;
    public int _tick_counter=0;


    public long _cur_tick_time=0;
    
    public int _order_signal=0;

    public double _lots=0;
    
    public int _o_last_signal=0;
    
    public double _equity=0;

    public int _rsi_up_flag=0;
    public int _rsi_down_flag=0;        
    
 
    
    // ********************************************************
    // ВНИМАНИЕ ТУТ ОПРЕДЕЛЯЮТСЯ ГЛОБАЛЬНЫЕ ПЕРЕМЕННЫЕ 
    // КОТОРЫЕ БУДУТ ВИДНЫ ИЗ ВСЕХ ФУНКЦИЙ
    // У НИХ ДОЛЖНЫ БЫТЬ УНИКАЛЬНЫЕ ИМЕНА
    
    public    double    reg=0;
    public    double    reg_pre=0;
    public    double    _major_long=0;
    public    double    _major_short=0;

   
    
    private ProfitLossHelper PLHelp;               
    
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();
        this.userChart = context.getChart(_currentInstrument);

        _context = context;
        _engine = context.getEngine();
        _indicators = context.getIndicators();
        _console = context.getConsole();
        _history = context.getHistory();
 
        _bar_CLOSE=0.0;
        _tagCounter=0;
        _start_time=System.currentTimeMillis();
        
        _order_fill_rejected=0;
        _order_submit_rejected=0;
        _order_submit_ok=0;
        _order_fill_ok=0;
        _order_label="";
        _is_close_buy_condition=0;
        _is_close_sell_condition=0;
        _is_open_buy_condition=0;
        _is_open_sell_condition=0;        
        
        _day=0;
        _hours=0;
        _minutes=0;
        
        _StopLoss_sell=_StopLoss;
        _TakeProfit_sell=_TakeProfit;
        
        _lots=lots;

        
    }


    public void onAccount(IAccount account) throws JFException {
                if ( this.account == null  ) {
            this.account = account;
            PLHelp = new ProfitLossHelper(account.getCurrency(),history);
        }        
    }

    public void onMessage(IMessage message) throws JFException {
                _message_order=message.getOrder();
                String status=new String();

        switch(message.getType())
        {
            case ORDER_FILL_REJECTED:
                if(_message_order.getInstrument()!=_currentInstrument) break;
                _console.getOut().println("ORDER FILL WAS REJECTED");
                _order_fill_rejected=1;
                if(_is_open_sell_condition==1) {
                    _is_open_sell_condition=0;                
                    if(_day==5 && _hours>21) {_sell_at_the_start_flag=1; return;}
                    if(_day>5 || _day==0) {_sell_at_the_start_flag=1; return;}        
                    openSellOrder();
                }
                if(_is_open_buy_condition==1) {
                    _is_open_buy_condition=0;
                    if(_day==5 && _hours>21) {_buy_at_the_start_flag=1; return;}
                    if(_day>5 || _day==0)     {_buy_at_the_start_flag=1; return;}
                    openBuyOrder();
                }
                break;
            case ORDER_SUBMIT_REJECTED:
                if(_message_order.getInstrument()!=_currentInstrument) break;            
                _console.getOut().println("ORDER SUBMIT WAS REJECTED");
                if(_is_open_sell_condition==1) {
                    _is_open_sell_condition=0;                
                    if(_day==5 && _hours>21) return;
                    if(_day>5 || _day==0) return;                
                    openSellOrder();
                }
                if(_is_open_buy_condition==1) {
                    _is_open_buy_condition=0;                
                    if(_day==5 && _hours>21) return;
                    if(_day>5 || _day==0) return;                
                    openBuyOrder();
                }
                break;
            case ORDER_SUBMIT_OK:
                if(_message_order.getInstrument()!=_currentInstrument) break;            
                _order_submit_ok=1;
                break;                                
            case ORDER_FILL_OK:
                if(_message_order.getInstrument()!=_currentInstrument) break;            
                _order_fill_ok=1;
                if(_is_open_sell_condition==1) _is_open_sell_condition=0;
                if(_is_open_buy_condition==1) _is_open_buy_condition=0;
                //_console.getOut().println("ORDER FILL OK");
                break;
            case ORDER_CLOSE_OK:
                if(_message_order.getInstrument()!=_currentInstrument) break;            
                if(_is_close_sell_condition==1) _is_close_sell_condition=0;
                if(_is_close_buy_condition==1) _is_close_buy_condition=0;
                
                    // 0 : secondary currency , 1 in account currency , 2 in pips
                    BigDecimal plData[] = PLHelp.calculateProfitLossData(_message_order);
                    if ( plData[0].signum() > 0 ) status += " in profit"; 
                    else if ( plData[0].signum() < 0 ) status += " for a loss"; 
                    else status += " at breakeven";
                    // net P/L : subtract commissions/fees here
                    // 
                    double commissionRate = 18;
                    BigDecimal netPL = plData[1].subtract(BigDecimal.valueOf(commissionRate).multiply(BigDecimal.valueOf(_message_order.getAmount())));
                    if ( netPL.signum() > 0 ) {
                        status += " ,net in profit"; 
                        //_lots=lots;
                    }
                    else if ( netPL.signum() < 0 ) {
                        status += " ,net for a loss"; 
                        //_lots=_lots*_martingale_mul;
                    }
                    else status += " ,net at breakeven";
                    
                    status += ": " + account.getCurrency() + " " + netPL.toString();
                    
                    _console.getOut().println("Order Status: "+status);
                    
                    
                
                                                
                break;
            case ORDER_CLOSE_REJECTED:
                if(_message_order.getInstrument()!=_currentInstrument) break;
                // Снова пытаемся закрыть ордер
                try {
                    if(message.getOrder().getState()!=IOrder.State.CLOSED || message.getOrder().getState()!=IOrder.State.CANCELED) message.getOrder().close();
                } catch(Exception e) {
                    _console.getOut().println("Exception trying to close order: "+e.toString());
                }
                break;
            case NEWS:
                //_console.getOut().println(message.getContent());
                break;
        }

        _order_fill_rejected=0;
        _order_submit_rejected=0;
        _order_submit_ok=0;
        _order_fill_ok=0;
        //_console.getOut().println("MESSAGE: "+message.getType());
    }

    public void onStop() throws JFException {
        try {
            if(remove_objects==1) userChart.removeAll();
        } catch(Exception e) {                        
            console.getOut().println("Exception "+e.toString());
        }
        

    }
    

    public void onTick(Instrument instrument, ITick tick) throws JFException {

        if (instrument != _currentInstrument) {
            return;
        }
      
       
        _bar_CLOSE=tick.getAsk();
        _bar_CLOSE_sell=tick.getBid();
        _tick_counter++;
        _cur_tick_time=tick.getTime();

         long curtimestamp=System.currentTimeMillis();        
        //_console.getOut().println("TIME: "+curtimestamp);
        

        
        
        
        
 
        
        if(_is_open_sell_condition==1 && curtimestamp-_order_sell_timestamp>15000) {
                    _console.getOut().println("REISSUE SELL ORDER");
                    _is_open_sell_condition=0;
                    if(_day==5 && _hours>21) {_sell_at_the_start_flag=1; return;}
                    if(_day>5 || _day==0) {_sell_at_the_start_flag=1; return;}        
                    openSellOrder();
                }
                
        if(_is_open_buy_condition==1 && curtimestamp-_order_buy_timestamp>15000) {
                    _console.getOut().println("REISSUE BUY ORDER");
                    _is_open_buy_condition=0;
                    if(_day==5 && _hours>21) {_buy_at_the_start_flag=1; return;}
                    if(_day>5 || _day==0)     {_buy_at_the_start_flag=1; return;}
                    openBuyOrder();
                }        
                
    
      // Trailing stop
      if(_trailing!=0) {          
      for (IOrder order : _engine.getOrders(_currentInstrument)) {

           if(order.getOrderCommand()==IEngine.OrderCommand.BUY) {
                if(tick.getBid()-order.getOpenPrice()>=_trailing*_currentInstrument.getPipValue()) {
                      if(round(tick.getBid()-_trailing*_currentInstrument.getPipValue(),5)>order.getStopLossPrice()) order.setStopLossPrice(round(tick.getBid()-_trailing*_currentInstrument.getPipValue(),5));
                }     
           }  
           
           if(order.getOrderCommand()==IEngine.OrderCommand.SELL) {
               
               if(order.getOpenPrice()-tick.getAsk()>=_trailing*_currentInstrument.getPipValue()) {                   
                      if(round(tick.getAsk()+_trailing*_currentInstrument.getPipValue(),5)<order.getStopLossPrice()) order.setStopLossPrice(round(tick.getAsk()+_trailing*_currentInstrument.getPipValue(),5));
                      if(order.getStopLossPrice()==0) order.setStopLossPrice(round(tick.getAsk()+_trailing*_currentInstrument.getPipValue(),5));
               }
           }         

      }      
      }
      
      // BreakEven
      if(_breakeven!=0) {          
      for (IOrder order : _engine.getOrders(_currentInstrument)) {

           if(order.getOrderCommand()==IEngine.OrderCommand.BUY) {
                if(tick.getBid()-order.getOpenPrice()>=_breakeven*_currentInstrument.getPipValue()) {
                      if(round(order.getOpenPrice()+_breakeven_pips*_currentInstrument.getPipValue(),5)>order.getStopLossPrice()) order.setStopLossPrice(round(order.getOpenPrice()+_breakeven_pips*_currentInstrument.getPipValue(),5));
                }
           }  
           
           if(order.getOrderCommand()==IEngine.OrderCommand.SELL) {
               
               if(order.getOpenPrice()-tick.getAsk()>=_breakeven*_currentInstrument.getPipValue()) {                   
                      if(round(order.getOpenPrice()-_breakeven_pips*_currentInstrument.getPipValue(),5)<order.getStopLossPrice()) order.setStopLossPrice(round(order.getOpenPrice()-_breakeven_pips*_currentInstrument.getPipValue(),5));                      
               }
           }         

      }      
      } 
        
    }


    // Here we get the new data from the chart
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        
        if (instrument != _currentInstrument) {
            return;
        }
        
        if (period != _currentPeriod) {
            return;
        }
        
        
        // ПРОВЕРЯЕМ ВРЕМЯ
        _signalBarTime=bidBar.getTime();

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(_signalBarTime);
                        
        _day=calendar.get(Calendar.DAY_OF_WEEK)-1;
        _hours=calendar.get(Calendar.HOUR_OF_DAY);
        _minutes=calendar.get(Calendar.MINUTE);        
        
        if(_day==5 && _hours>20) return;
        if(_day>5 || (_day==0 && _hours<21)) return;
        
        
        if (_UseHourTrade==1) {
              if (!(_hours >= _FromHourTrade && _hours <= _ToHourTrade)) {
                 return;
              }
        }      
        
                double leverage=this.account.getLeverage();
                _equity=this.account.getEquity();
                
                if(_equity>0) {
                    _lots=(((_equity*leverage)/100)*lots)/1000000;
                } else _lots=0.1;          
        
        // **********************************************************************************************


     double outputs = _indicators.trix(_currentInstrument, _currentPeriod,_indOfferside,IIndicators.AppliedPrice.CLOSE,_TimePeriod,0);
                
     double outputs1 = _indicators.trix(_currentInstrument, _currentPeriod,_indOfferside,IIndicators.AppliedPrice.CLOSE,_TimePeriod,1);
        
         // Параметры индикатора
     reg =_indicators.ht_trendmode(_currentInstrument,_currentPeriod,_indOfferside,IIndicators.AppliedPrice.CLOSE,0);
          
     reg_pre =_indicators.ht_trendmode(_currentInstrument,_currentPeriod,_indOfferside,IIndicators.AppliedPrice.CLOSE,1);
        
        //_console.getOut().println(outputs2);
        
        if(outputs> -0.005) _rsi_up_flag=0;
        if(outputs< 0.005) _rsi_down_flag=0;
        // Условие на бай
        
        if(_rsi_up_flag==0  && reg<reg_pre && outputs < -0.0048  ) {
                _console.getOut().println("SELL");
                _is_open_sell_condition=0;                                                 
                openSellOrder();
                closeBuyOrders();

                _rsi_up_flag=1;
        }

        if(_rsi_down_flag==0 && reg>reg_pre && outputs >0.0052 ) {
                _console.getOut().println("BUY");
                _is_open_buy_condition=0;      
                openBuyOrder();
                closeSellOrders();                                           
                _rsi_down_flag=1;
        }       
        
     
       
                         
                                    
    }
          



 




 





    public  File getMyStrategiesDir() {
        File myDocs = FileSystemView.getFileSystemView().getDefaultDirectory();
        File dir = new File(myDocs.getPath() + File.separator + "My Strategies" + File.separator + "files");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
    
    
    
    
// Функции для работы с ордерами

protected String getOrderSignalLabel(int signal) {
    StringBuffer buff=new StringBuffer();
    buff.append("_SIGNAL_"+signal+"_");
    
    return buff.toString();
}

    
protected String getLabel(Instrument instrument) {
        StringBuffer buff = new StringBuffer(instrument.name().toLowerCase());
        buff.append(_start_time);
        buff.append(System.currentTimeMillis());
        buff.append(_tagCounter++);
        buff.append(getOrderSignalLabel(_order_signal));
        return buff.toString();
    }
    
protected int positionsTotal(Instrument instrument) throws JFException {
        int counter = 0;
        for (IOrder order : _engine.getOrders(instrument)) {
            if (order.getState() == IOrder.State.FILLED) {
                counter++;
            }
        }
        return counter;
    }
    
protected int positionsBuyTotal(Instrument instrument) throws JFException {
        int counter = 0;
        for (IOrder order : _engine.getOrders(instrument)) {
            if (order.getState() == IOrder.State.FILLED && order.isLong()) {
                counter++;
            }
        }
        return counter;
    }
    
protected int positionsSellTotal(Instrument instrument) throws JFException {
        int counter = 0;
        for (IOrder order : _engine.getOrders(instrument)) {
            if (order.getState() == IOrder.State.FILLED && !order.isLong()) {
                counter++;
            }
        }
        return counter;
    }
    
protected int positionsBuySignalTotal(Instrument instrument) throws JFException {
        int counter = 0;
        for (IOrder order : _engine.getOrders(instrument)) {
            if (order.isLong() && order.getState() == IOrder.State.FILLED) {
                ///_console.getOut().println("BUY "+order.getId());
                if(order.getLabel().contains(getOrderSignalLabel(_order_signal))) counter++;
            }
        }
        // check for signal orders MAKE BETTER IN FUTURE
        // LOCKED SIGNALS LIST
        if(counter>0) return 0;
        else return 1;
    }    
protected int positionsSellSignalTotal(Instrument instrument) throws JFException {
        int counter = 0;
        for (IOrder order : _engine.getOrders(instrument)) {
            if (!order.isLong() && order.getState() == IOrder.State.FILLED) {
                //_console.getOut().println("SELL "+order.getId());
                if(order.getLabel().contains(getOrderSignalLabel(_order_signal))) counter++;
            }
        }
        // check for signal orders MAKE BETTER IN FUTURE
        // LOCKED SIGNALS LIST
        if(counter>0) return 0; 
        else return 1;
}    

protected void openBuyOrder() throws JFException {
    if(_is_open_buy_condition==0 && positionsBuyTotal(_currentInstrument)<_maxpos) {
                double _bar_CLOSE=_history.getLastTick(_currentInstrument).getAsk();
                // Calculating StopLoss
                double tempSL=0;
                double tempTP=0;
                if(_StopLoss>0) {
                    tempSL=_bar_CLOSE - _currentInstrument.getPipValue() * _StopLoss;
                }
                if(_TakeProfit>0) {
                    tempTP=_bar_CLOSE + _currentInstrument.getPipValue() * _TakeProfit;
                }

                // Opening order!        
                _order_buy_timestamp=System.currentTimeMillis();
                                        
                _order_label=getLabel(_currentInstrument);
                _is_open_buy_condition=1;
                _engine.submitOrder(
                            _order_label, _currentInstrument, 
                            IEngine.OrderCommand.BUY, _lots, _bar_CLOSE, 
                            0, tempSL, 
                            tempTP
                            );

    }
}    

protected void openSellOrder() throws JFException {
    if(_is_open_sell_condition==0 && positionsSellTotal(_currentInstrument)<_maxpos_sell) {
                double _bar_CLOSE_sell=_history.getLastTick(_currentInstrument).getBid();
                // Calculating StopLoss
                double tempSL=0;
                double tempTP=0;
                if(_StopLoss_sell>0) {
                    tempSL=_bar_CLOSE_sell + _currentInstrument.getPipValue() * _StopLoss_sell;
                }
                if(_TakeProfit_sell>0) {
                    tempTP=_bar_CLOSE_sell - _currentInstrument.getPipValue() * _TakeProfit_sell;
                }

                // Opening order!                
                _order_label=getLabel(_currentInstrument);

                _order_sell_timestamp=System.currentTimeMillis();
                
                _is_open_sell_condition=1;                
                _engine.submitOrder(
                            _order_label+"S", _currentInstrument, 
                            IEngine.OrderCommand.SELL, _lots, _bar_CLOSE_sell, 
                            0, tempSL, 
                            tempTP
                            );
    }                
}


protected void closeOrders() throws JFException {    
        for (IOrder order : _engine.getOrders(_currentInstrument)) {
            if (order.getState() == IOrder.State.FILLED) {
                    order.close();
            }
        }            
}


// Доработать закрытие ордеров!
protected void closeBuyOrders() throws JFException {
    if(_is_close_buy_condition==0) {
        for (IOrder order : _engine.getOrders(_currentInstrument)) {
            if (order.getState() == IOrder.State.FILLED && order.isLong()) {
                    order.close();
                    _is_close_buy_condition=1;
            }
        }                
    }    
}

protected void closeSellOrders() throws JFException {    
    if(_is_close_sell_condition==0) {
        for (IOrder order : _engine.getOrders(_currentInstrument)) {
            if (order.getState() == IOrder.State.FILLED && !order.isLong()) {
                    order.close();
                    _is_close_sell_condition=1;
            }
        }            
    }
}    
    
    
  public  double round(double d, int decimalPlace){
    // see the Javadoc about why we use a String in the constructor
    // http://java.sun.com/j2se/1.5.0/docs/api/java/math/BigDecimal.html#BigDecimal(double)
    BigDecimal bd = new BigDecimal(Double.toString(d));
    bd = bd.setScale(decimalPlace,BigDecimal.ROUND_HALF_UP);
    int lastdigit=0;
    
    char digits[]=bd.toString().toCharArray();
    lastdigit=digits[digits.length-1]-48;
   // _console.getOut().println("LD: "+lastdigit);
    
    if(lastdigit!=0 || lastdigit!=5) {
        if(lastdigit>5) {           
               digits[digits.length-1]=53;
        }
        if(lastdigit<5) {
               digits[digits.length-1]=48;
        }
        
    }
    
    BigDecimal bd1 = new BigDecimal(digits);
    bd1 = bd1.setScale(decimalPlace,BigDecimal.ROUND_HALF_UP);

    //_console.getOut().println("D: "+bd1.toString()+" L: "+digits.length);
    
    return bd1.doubleValue();
    
  }
  
}


class ProfitLossHelper {
    private Currency baseCurrency;
    private IHistory history;
    private static BigDecimal DukaLotSize = BigDecimal.valueOf(1000000);
    protected static Map<Currency, Instrument> pairs = new HashMap<Currency, Instrument>();

    static {
        pairs.put(Currency.getInstance("AUD"), Instrument.AUDUSD);
        pairs.put(Currency.getInstance("CAD"), Instrument.USDCAD);
        pairs.put(Currency.getInstance("CHF"), Instrument.USDCHF);
        pairs.put(Currency.getInstance("DKK"), Instrument.USDDKK);
        pairs.put(Currency.getInstance("EUR"), Instrument.EURUSD);
        pairs.put(Currency.getInstance("GBP"), Instrument.GBPUSD);
        pairs.put(Currency.getInstance("HKD"), Instrument.USDHKD);
        pairs.put(Currency.getInstance("JPY"), Instrument.USDJPY);
        pairs.put(Currency.getInstance("MXN"), Instrument.USDMXN);
        pairs.put(Currency.getInstance("NOK"), Instrument.USDNOK);
        pairs.put(Currency.getInstance("NZD"), Instrument.NZDUSD);
        pairs.put(Currency.getInstance("SEK"), Instrument.USDSEK);
        pairs.put(Currency.getInstance("SGD"), Instrument.USDSGD);
        pairs.put(Currency.getInstance("TRY"), Instrument.USDTRY);
    }    

    public ProfitLossHelper (Currency baseCurrency, IHistory history) {
        this.baseCurrency = baseCurrency;
        this.history = history;    
    }

    public double calculateProfitLoss(IOrder order) throws JFException {
        double closePrice;
        if (order.getState() == IOrder.State.CLOSED) {
            closePrice = order.getClosePrice();
        } else {
            ITick tick = history.getLastTick(order.getInstrument());
            if (order.getOrderCommand().isLong()) {
                closePrice = tick.getBid();
            } else {
                closePrice = tick.getAsk();
            }
        }
        BigDecimal profLossInSecondaryCCY;
        if (order.getOrderCommand().isLong()) {
            profLossInSecondaryCCY = BigDecimal.valueOf(closePrice).subtract(BigDecimal.valueOf(order.getOpenPrice()))
            .multiply(BigDecimal.valueOf(order.getAmount()).multiply(DukaLotSize)).setScale(2, BigDecimal.ROUND_HALF_EVEN);
        } else {
            profLossInSecondaryCCY = BigDecimal.valueOf(order.getOpenPrice()).subtract(BigDecimal.valueOf(closePrice))
            .multiply(BigDecimal.valueOf(order.getAmount()).multiply(DukaLotSize)).setScale(2, BigDecimal.ROUND_HALF_EVEN);
        }
        OfferSide side = order.getOrderCommand().isLong() ? OfferSide.ASK : OfferSide.BID;
        BigDecimal convertedProfLoss = convert(profLossInSecondaryCCY, order.getInstrument().getSecondaryCurrency(),
                baseCurrency, side).setScale(2, BigDecimal.ROUND_HALF_EVEN);
        return convertedProfLoss.doubleValue();
    }

    public BigDecimal[] calculateProfitLossData(IOrder order) throws JFException {
        BigDecimal[] res = new BigDecimal[3];
        res[0] = BigDecimal.ZERO;
        res[1] = BigDecimal.ZERO;
        res[2] = BigDecimal.ZERO;
        double closePrice;
        if (order.getState() == IOrder.State.CLOSED) {
            closePrice = order.getClosePrice();
        } else {
            ITick tick = history.getLastTick(order.getInstrument());
            if (order.getOrderCommand().isLong()) {
                closePrice = tick.getBid();
            } else {
                closePrice = tick.getAsk();
            }
        }
        BigDecimal profLossInSecondaryCCY;
        if (order.getOrderCommand().isLong()) {
            profLossInSecondaryCCY = BigDecimal.valueOf(closePrice).subtract(BigDecimal.valueOf(order.getOpenPrice()))
            .multiply(BigDecimal.valueOf(order.getAmount()).multiply(DukaLotSize)).setScale(2, BigDecimal.ROUND_HALF_EVEN);
            res[2] = BigDecimal.valueOf(closePrice).subtract(BigDecimal.valueOf(order.getOpenPrice()))
            .multiply(BigDecimal.valueOf(10).pow(order.getInstrument().getPipScale())).setScale(1, BigDecimal.ROUND_HALF_EVEN);
        } else {
            profLossInSecondaryCCY = BigDecimal.valueOf(order.getOpenPrice()).subtract(BigDecimal.valueOf(closePrice))
            .multiply(BigDecimal.valueOf(order.getAmount()).multiply(DukaLotSize)).setScale(2, BigDecimal.ROUND_HALF_EVEN);
            res[2] = BigDecimal.valueOf(order.getOpenPrice()).subtract(BigDecimal.valueOf(closePrice))
            .multiply(BigDecimal.valueOf(10).pow(order.getInstrument().getPipScale())).setScale(1, BigDecimal.ROUND_HALF_EVEN);
        }
        res[0] = profLossInSecondaryCCY;
        OfferSide side = order.getOrderCommand().isLong() ? OfferSide.ASK : OfferSide.BID;
        res[1] = convert(profLossInSecondaryCCY, order.getInstrument().getSecondaryCurrency(),
                baseCurrency, side).setScale(2, BigDecimal.ROUND_HALF_EVEN);        
        return res;
    }
    
    
    
    public BigDecimal convert(BigDecimal amount, Currency sourceCurrency, Currency targetCurrency, OfferSide side) throws JFException {
        if (targetCurrency.equals(sourceCurrency)) {
            return amount;
        }

        BigDecimal dollarValue;
        if (sourceCurrency.equals(Instrument.EURUSD.getSecondaryCurrency())) {
            dollarValue = amount;
        } else {
            Instrument helperSourceCurrencyPair = pairs.get(sourceCurrency);
            if (helperSourceCurrencyPair == null) {
                throw new IllegalArgumentException("No currency pair found for " + sourceCurrency);
            }

            BigDecimal helperSourceCurrencyPrice = getLastPrice(helperSourceCurrencyPair, side);
            if (null == helperSourceCurrencyPrice) return null;

            dollarValue = helperSourceCurrencyPair.toString().indexOf("USD") == 0 ?
                    amount.divide(helperSourceCurrencyPrice, 2, RoundingMode.HALF_EVEN)
                    : amount.multiply(helperSourceCurrencyPrice).setScale(2, RoundingMode.HALF_EVEN);
        }

        if (targetCurrency.equals(Instrument.EURUSD.getSecondaryCurrency())) {
            return dollarValue;
        }

        Instrument pair = pairs.get(targetCurrency);
        BigDecimal price = getLastPrice(pair, side);
        if (null == price) return null;

        BigDecimal result = pair.toString().indexOf("USD") == 0 ?
                dollarValue.multiply(price).setScale(2, RoundingMode.HALF_EVEN)
                : dollarValue.divide(price, 2, RoundingMode.HALF_EVEN);

                return result;
    }

    protected BigDecimal getLastPrice(Instrument pair, OfferSide side) throws JFException {
        ITick tick = history.getLastTick(pair);
        if (tick == null) {
            return null;
        }
        if (side == OfferSide.BID) {
            return BigDecimal.valueOf(tick.getBid());
        } else {
            return BigDecimal.valueOf(tick.getAsk());
        }
    }

}