package com.universalimporter.domain.transformation;

/**
 * A value could not be transformed. Checked on purpose: the engine must turn it into a row error rather than let
 * it stop the import (design T1). The message is English and never contains the cell value (design D13).
 */
public final class TransformationFailure extends Exception {

    public TransformationFailure(String message) {
        super(message);
    }
}
