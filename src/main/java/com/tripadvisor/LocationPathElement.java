package com.tripadvisor;

/**
 * @author Erdinc Yilmazel (eyilmazel@tripadvisor.com)
 * @since 6/17/13
 */
public class LocationPathElement
{
    final int id;
    final int placeType;
    final String name;

    public LocationPathElement(int id, int placeType, String name)
    {
        this.id = id;
        this.placeType = placeType;
        this.name = name;
    }

    public int getId()
    {
        return id;
    }

    public int getPlaceType()
    {
        return placeType;
    }

    public String getName()
    {
        return name;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        LocationPathElement that = (LocationPathElement) o;

        return id == that.id;
    }

    @Override
    public int hashCode()
    {
        return id;
    }

    @Override
    public String toString()
    {
        return String.valueOf(id);
    }
}
