package com.mhm.mhm2027.terminal;

import java.io.Serial;

/** A script was refused: bad header, unknown API, an API above your level, and so on. */
public class InstallException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    public InstallException(String message) {
        super(message);
    }
}
