package com.parvez.urlshortener.service;

import com.parvez.urlshortener.exception.InvalidUrlException;
import com.parvez.urlshortener.exception.SafetyScanUnavailableException;
import com.parvez.urlshortener.safety.AddressResolver;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Normalizes HTTP URLs and rejects ambiguous hosts or configured internal networks before scanning. */
public class DestinationPolicy {
    private final AddressResolver resolver;
    private final List<Network> networks = new ArrayList<>();

    /** Adds operator CIDRs to mandatory non-public IPv4/IPv6 ranges. */
    public DestinationPolicy(AddressResolver resolver, String extraCidrs) {
        this.resolver = resolver;
        String defaults = "0.0.0.0/8,10.0.0.0/8,100.64.0.0/10,127.0.0.0/8,169.254.0.0/16,172.16.0.0/12,192.168.0.0/16,192.0.0.0/24,192.0.2.0/24,198.18.0.0/15,198.51.100.0/24,203.0.113.0/24,224.0.0.0/4,240.0.0.0/4,::/96,::1/128,fc00::/7,fe80::/10,ff00::/8,2001:db8::/32,2002::/16,64:ff9b::/96";
        for (String cidr : (defaults + "," + extraCidrs).split(",")) {
            if (cidr.isBlank()) continue;
            try {
                String[] parts = cidr.trim().split("/", -1);
                if (parts.length != 2 || !parts[0].matches("[0-9a-fA-F:.]+")) throw new IllegalArgumentException();
                byte[] address = InetAddress.getByName(parts[0]).getAddress();
                int bits = Integer.parseInt(parts[1]);
                if (bits < 0 || bits > address.length * 8) throw new IllegalArgumentException();
                networks.add(new Network(address, bits));
            } catch (Exception ex) { throw new IllegalArgumentException("Invalid safety CIDR configuration"); }
        }
    }

    /** Preserves raw path/query semantics and removes fragments before checking every resolved address. */
    public URI normalize(String input) {
        try {
            if (input == null || input.length() > 2048) throw invalid();
            URI uri = new URI(input.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (!(scheme.equals("http") || scheme.equals("https")) || host == null || uri.getRawUserInfo() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0 || host.contains("%")) throw invalid();
            host = host.toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            // Reject shorthand/numeric encodings browsers may reinterpret differently from DNS.
            if (!host.startsWith("[") && (host.matches("[0-9.]+") && !host.matches("([0-9]{1,3}\\.){3}[0-9]{1,3}")
                    || host.matches("(?i)(0x[0-9a-f]+|0[0-9]+)(\\..*)?"))) throw invalid();
            if (host.matches("[0-9.]+")) for (String part : host.split("\\.")) {
                if (Integer.parseInt(part) > 255 || part.length() > 1 && part.startsWith("0")) throw invalid();
            }
            String dnsHost = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
            InetAddress[] addresses;
            try { addresses = resolver.resolve(dnsHost); }
            catch (java.net.UnknownHostException ex) { throw new SafetyScanUnavailableException(); }
            if (addresses.length == 0) throw new SafetyScanUnavailableException();
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || networks.stream().anyMatch(network -> network.contains(address.getAddress()))) throw invalid();
            }
            int port = uri.getPort();
            String authority = host + (port == -1 || scheme.equals("https") && port == 443 || scheme.equals("http") && port == 80 ? "" : ":" + port);
            String path = uri.getRawPath();
            return URI.create(scheme + "://" + authority + (path == null || path.isEmpty() ? "/" : path)
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())).normalize();
        } catch (InvalidUrlException | SafetyScanUnavailableException ex) { throw ex; }
        catch (Exception ex) { throw invalid(); }
    }

    private InvalidUrlException invalid() { return new InvalidUrlException("Destination must be a public HTTP(S) URL without credentials"); }

    private record Network(byte[] address, int bits) {
        boolean contains(byte[] candidate) {
            if (candidate.length != address.length) return false;
            for (int bit = 0; bit < bits; bit++) {
                int mask = 1 << (7 - bit % 8);
                if ((candidate[bit / 8] & mask) != (address[bit / 8] & mask)) return false;
            }
            return true;
        }
    }
}
