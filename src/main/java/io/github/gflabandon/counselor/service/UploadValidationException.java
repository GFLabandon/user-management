package io.github.gflabandon.counselor.service;

import java.io.IOException;

/** Contains a deliberate user-facing message, never a filesystem or decoder error. */
public class UploadValidationException extends IOException {
    public UploadValidationException(String message) { super(message); }
}
