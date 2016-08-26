package com.tripadvisor;

public enum PlaceType
{
    ATTRACTION(10021), RESTAURANT(10022), ACCOMMODATION(10023);

    int m_nPlaceTypeId;

    PlaceType(int nPlaceTypeId)
    {
        this.m_nPlaceTypeId = nPlaceTypeId;
    }

    public int getPlaceTypeId()
    {
        return m_nPlaceTypeId;
    }

    public String toString()
    {
        String lowerCaseName = name().toLowerCase();
        return lowerCaseName.substring(0, 1).toUpperCase() + lowerCaseName.substring(1);
    }

    public String getPlaceTypeAsString()
    {
        return "" + m_nPlaceTypeId;
    }

    public static PlaceType getById(int nPlaceTypeId)
    {
        switch (nPlaceTypeId)
        {
        case 10023:
            return ACCOMMODATION;
        case 10021:
            return ATTRACTION;
        case 10022:
            return RESTAURANT;
        case 0:
            return null;
        default:
            return null;
        }
    }

}
