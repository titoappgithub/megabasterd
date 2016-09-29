package megabasterd;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import static megabasterd.MiscTools.formatBytes;

public final class SpeedMeter implements Runnable, SecureNotifiable
{
    public static final int SLEEP = 3000;
    private long _progress;
    private final Transference _transference;
    private final GlobalSpeedMeter _gspeed;
    private volatile long _lastSpeed;
    private volatile boolean _exit;
    private final Object _secure_notify_lock;
    private boolean _notified;
    
  
    SpeedMeter(Transference transference, GlobalSpeedMeter gspeed)
    {
        _notified = false;
        _secure_notify_lock = new Object();
        _transference = transference;
        _progress = transference.getProgress();
        _lastSpeed=0;
        _gspeed = gspeed;
        _exit=false;
    }
    
    @Override
    public void secureNotify()
    {
        synchronized(_secure_notify_lock) {
      
            _notified = true;
            
            _secure_notify_lock.notify();
        }
    }
    
    @Override
    public void secureWait() {
        
        synchronized(_secure_notify_lock)
        {
            while(!_notified) {

                try {
                    _secure_notify_lock.wait();
                } catch (InterruptedException ex) {
                    Logger.getLogger(UploadMACGenerator.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            
            _notified = false;
        }
    }
    
    @Override
    public void secureNotifyAll() {
        
        synchronized(_secure_notify_lock) {
            
            _notified = true;
      
            _secure_notify_lock.notifyAll();
        }
    }
    

    public void setExit(boolean exit) {
        _exit = exit;
    }
   
    public long getLastSpeed()
    {
        return _lastSpeed;
    }
    
    public void setLastSpeed(long speed)
    {
        _lastSpeed = speed;
    }
    
    @Override
    public void run()
    { 
        System.out.println("SpeedMeter hello!");
        
        long p, sp=0;
        int no_data_count;
        
        _transference.getView().updateSpeed("------", true);
        _transference.getView().updateRemainingTime("--d --:--:--", true);

            try
            {
                no_data_count = 0;
                
                while(!_exit)
                {
                    Thread.sleep(SpeedMeter.SLEEP*(no_data_count+1));
                    
                    if(!_exit)
                    { 
                        p = _transference.getProgress();

                        if(_transference.isPaused()) {

                            _transference.getView().updateSpeed("------", true);
                            
                            _transference.getView().updateRemainingTime("--d --:--:--", true);

                            setLastSpeed(0);

                            _gspeed.secureNotify();
                            
                            secureWait();

                        } else if( p > _progress) {
                            
                            double sleep_time = ((double)SpeedMeter.SLEEP*(no_data_count+1))/1000 ;
                            
                            double current_speed = (p - _progress) / sleep_time;

                            _progress = p;

                            sp = Math.round(current_speed);

                            if(sp > 0) {

                                _transference.getView().updateSpeed(formatBytes(sp)+"/s", true);
 
                                _transference.getView().updateRemainingTime(calculateRemTime((long)Math.floor((_transference.getFile_size()-p)/sp ) ), true);
                                
                                setLastSpeed(sp);

                                _gspeed.secureNotify();
                            }

                            no_data_count=0;
                            
                        } else {
                            
                            _transference.getView().updateSpeed("------", true);
                            
                            _transference.getView().updateRemainingTime("--d --:--:--", true);
                            
                            setLastSpeed(0);

                            _gspeed.secureNotify();
                            
                            no_data_count++;
                        }
                    }
                }
            }
            catch (InterruptedException ex)
            {
                
            }
        
    }
    
    private String calculateRemTime(long seconds)
    {
        int days = (int) TimeUnit.SECONDS.toDays(seconds);
        
        long hours = TimeUnit.SECONDS.toHours(seconds) -
                     TimeUnit.DAYS.toHours(days);
        
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) - 
                      TimeUnit.DAYS.toMinutes(days) -
                      TimeUnit.HOURS.toMinutes(hours);
        
        long secs = TimeUnit.SECONDS.toSeconds(seconds) -
                      TimeUnit.DAYS.toSeconds(days) -
                      TimeUnit.HOURS.toSeconds(hours) - 
                      TimeUnit.MINUTES.toSeconds(minutes);
        
        return String.format("%dd %d:%02d:%02d", days, hours, minutes, secs);
    }
}