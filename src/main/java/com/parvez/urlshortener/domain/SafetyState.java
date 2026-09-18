package com.parvez.urlshortener.domain;

/** Determines whether a destination may be used for public redirects. */
public enum SafetyState { PENDING, ACTIVE, REJECTED, SCAN_FAILED }
