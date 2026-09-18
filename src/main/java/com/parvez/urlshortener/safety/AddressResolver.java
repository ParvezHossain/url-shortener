package com.parvez.urlshortener.safety;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Resolves all destination addresses without connecting to the destination. */
@FunctionalInterface
public interface AddressResolver {
    /** Returns all A/AAAA addresses or fails closed if resolution is unavailable. */
    InetAddress[] resolve(String host) throws UnknownHostException;
}
