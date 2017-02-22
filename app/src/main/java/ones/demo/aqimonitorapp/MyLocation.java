package ones.demo.aqimonitorapp;

/**
 * Created by OneS on 2017/2/21.
 */

public class MyLocation {
    private double Latitude = 0.0;
    private double Longitude = 0.0;

    public MyLocation(double Longitude, double Latitude)
    {
        this.Latitude = Latitude;
        this.Longitude = Longitude;
    }

    public void setLatitude(double Latitude)
    {
        this.Latitude = Latitude;
    }

    public void setLongitude(double Longitude)
    {
        this.Longitude = Longitude;
    }

    public double getLatitude()
    {
        return Latitude;
    }

    public double getLongitude()
    {
        return Longitude;
    }
}
