package io.github.gflabandon.counselor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class DisabledAccountAuthenticationTests {
    @Test void disabledAccountStillChecksPasswordToPreventAttributeTimingDisclosure() {
        var encoder = spy(PasswordEncoderFactories.createDelegatingPasswordEncoder());
        String hash = encoder.encode("test-disabled-password");
        var account = User.withUsername("disabled-test").password(hash).roles("VIEWER").disabled(true).build();
        var provider = new DaoAuthenticationProvider(username -> account);
        provider.setPasswordEncoder(encoder);
        assertThatThrownBy(() -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("disabled-test", "test-disabled-password")))
                .isInstanceOf(DisabledException.class);
        // Check work performed, not noisy elapsed-time thresholds.
        verify(encoder, atLeastOnce()).matches("test-disabled-password", hash);
    }
}
