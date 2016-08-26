package com.tripadvisor;

import com.google.common.base.Strings;
import java.lang.Override;

/**
 * This class represents a Matching request. It has getters and setters for the various fields of a matching request.
 * 
 * @author msrivastava
 * @since March 28, 2016
 *
 */
public class MatchingRequest
{

    protected PlaceType placeType;
    protected String countryName;
    protected String region;
    protected String city;
    protected String name;
    protected String street;
    protected String phone;
    protected String postalCode;
    protected String locationId;
    protected String whatType;
//    protected Country country;
    protected String phoneNormalized;
    protected String latitude;
    protected String longitude;
    protected String url;
    protected String category;
    

//    public MatchingRequest(PlaceType placeType, String country, String region, String city, String name, String street, String phone, String postalCode,  String url)
//    {
//        this.placeType = placeType;
//        setCountryName(country);
//        this.region = region;
//        this.city = city;
//        this.name = name;
//        this.street = street;
//        setPhone(phone);
//        this.postalCode = postalCode;
//        this.url=url;
//    }

    public String getCountryName()
    {
        return countryName;
    }

//    protected void setCountryName(String country)
//    {
//        this.countryName = country;
//        this.country = Country.match(country);
//    }

    public String getRegion()
    {
        return region;
    }

    public String getCity()
    {
        return city;
    }

    public String getName()
    {
        return name;
    }

    public String getStreet()
    {
        return street;
    }

    public String getPhone()
    {
        return phone;
    }

    public String getUrl()
    {
        return url;
    }
    
//    protected void setPhone(String phone)
//    {
//        this.phone = phone;
//        if (!Strings.isNullOrEmpty(phone) && country != null)
//        {
//            this.phoneNormalized = Strings.isNullOrEmpty(country.getCc2()) ? phone : PhoneNormalizer.normalize(phone, country.getCc2());
//        }
//    }

    public String getPostalCode()
    {
        return postalCode;
    }

    public PlaceType getPlaceType()
    {
        return placeType;
    }

//    public Country getCountry()
//    {
//        return country;
//    }

    public String getPhoneNormalized()
    {
        return phoneNormalized;
    }

    public String getLocationId()
    {
        return locationId;
    }
    
    public void setLatitude(String latitude)
    {
        this.latitude = latitude;
    }
    
    public void setLongitude(String longitude)
    {
        this.longitude = longitude;
    }
    
    
    public String getLatitude()
    {
        return latitude;
    }
    
    public String getLongitude()
    {
        return longitude;
    }
    
    public void setLocationId(String locationId)
    {
        this.locationId = locationId;
    }
    
    public void setWhatType(String whatType)
    {
        this.whatType = whatType;
    }
    
    public String getWhatType()
    {
        return this.whatType;
    }
       

    @Override
    public String toString()
    {
        return "{ Location Id:" + locationId +  ", PlaceType:" + placeType + ", Country:" + countryName + ", Region:" + region + ", City:" + city + ", " +
                "Name:" + name + ", Street:" + street + ", PostalCode:" + postalCode + ", PhoneNumber:" + phone + ", Latitude:" + latitude + ", Longitude:" + longitude +
                "}";
    }

    public void setCategory(String category)
    {
        this.category = category;       
    }
    
    public String getCategory()
    {
        return this.category;       
    }
}
