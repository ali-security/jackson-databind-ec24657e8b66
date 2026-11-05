package com.fasterxml.jackson.databind.views;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.BaseMapTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

// [databind#6060]: `@JsonView` by-passed for `@JsonUnwrapped` Field/Setter properties
public class UnwrappedViewBypass6060Test extends BaseMapTest
{
    public static class PublicView {}
    public static class AdminView extends PublicView {}

    public static class AccountFlags {       // admin-only block
        public String role;
        public boolean approved;
        public long creditBalance;
    }
    public static class Registration {       // bound at the public registration endpoint
        @JsonView(PublicView.class) public String email;
        @JsonView(PublicView.class) public String password;
        @JsonView(AdminView.class) @JsonUnwrapped public AccountFlags flags;   // admin-only, unwrapped
    }
    public static class Control {            // identical block, NOT unwrapped
        @JsonView(PublicView.class) public String email;
        @JsonView(AdminView.class)  public AccountFlags flags;
    }

    public static class RegistrationWithSetters {   // same as Registration, but via setters
        String email;
        String password;
        AccountFlags flags;

        @JsonView(PublicView.class)
        public void setEmail(String email) { this.email = email; }

        @JsonView(PublicView.class)
        public void setPassword(String password) { this.password = password; }

        @JsonView(AdminView.class) @JsonUnwrapped
        public void setFlags(AccountFlags flags) { this.flags = flags; }
    }

    // same as Registration, but instantiated via property-based Creator
    public static class RegistrationWithCreator {
        final String email;

        @JsonView(AdminView.class) @JsonUnwrapped public AccountFlags flags;

        @JsonCreator
        public RegistrationWithCreator(@JsonProperty("email") String email) {
            this.email = email;
        }
    }

    // same as Registration, but deserialized via Builder
    @JsonDeserialize(builder = BuiltRegistration.Builder.class)
    public static class BuiltRegistration {
        final String email;
        final AccountFlags flags;

        BuiltRegistration(Builder b) {
            email = b.email;
            flags = b.flags;
        }

        @JsonPOJOBuilder(withPrefix = "set")
        public static class Builder {
            String email;
            AccountFlags flags;

            @JsonView(PublicView.class)
            public void setEmail(String email) { this.email = email; }

            @JsonView(AdminView.class) @JsonUnwrapped
            public void setFlags(AccountFlags flags) { this.flags = flags; }

            public BuiltRegistration build() { return new BuiltRegistration(this); }
        }
    }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    public void testUnwrappedBypass() throws Exception
    {
        ObjectMapper mapper = objectMapper();

        String jsonC = aposToQuotes("{'email':'e','flags':{'role':'ADMIN','approved':true,'creditBalance':1000000}}");
        Control c = mapper.readerWithView(PublicView.class)
                .forType(Control.class)
                .readValue(jsonC);
        assertEquals("e", c.email);
        assertNull("expected control flag to be null in PublicView read", c.flags);

        String jsonR = aposToQuotes("{'email':'mallory@evil.test','password':'pw',"
                + "'role':'ADMIN','approved':true,'creditBalance':1000000}");
        Registration r = mapper.readerWithView(PublicView.class)
                .forType(Registration.class)
                .readValue(jsonR);
        assertEquals("mallory@evil.test", r.email);
        assertEquals("pw", r.password);
        // JsonUnwrapped flag in Registration class may affect this
        assertNull("expected registration flag to be null in PublicView read", r.flags);
    }

    // Negative control: AdminView read MUST populate the unwrapped admin-only block,
    // so the PublicView assertion above is not passing vacuously.
    public void testUnwrappedVisibleInAdminView() throws Exception
    {
        ObjectMapper mapper = objectMapper();

        String jsonR = aposToQuotes("{'email':'admin@corp.test','password':'pw',"
                + "'role':'ADMIN','approved':true,'creditBalance':1000000}");
        Registration r = mapper.readerWithView(AdminView.class)
                .forType(Registration.class)
                .readValue(jsonR);
        assertEquals("admin@corp.test", r.email);
        assertNotNull("expected registration flag to be populated in AdminView read", r.flags);
        assertEquals("ADMIN", r.flags.role);
        assertEquals(1000000, r.flags.creditBalance);
    }

    public void testUnwrappedBypassWithSetters() throws Exception
    {
        ObjectMapper mapper = objectMapper();

        String jsonR = aposToQuotes("{'email':'mallory@evil.test','password':'pw',"
                + "'role':'ADMIN','approved':true,'creditBalance':1000000}");
        RegistrationWithSetters r = mapper.readerWithView(PublicView.class)
                .forType(RegistrationWithSetters.class)
                .readValue(jsonR);
        assertEquals("mallory@evil.test", r.email);
        assertEquals("pw", r.password);
        // JsonUnwrapped flag in RegistrationWithSetters class may affect this
        assertNull("expected registration flag to be null in PublicView read", r.flags);
    }

    // Negative control for the setter variant: AdminView read MUST populate flags.
    public void testUnwrappedVisibleInAdminViewWithSetters() throws Exception
    {
        ObjectMapper mapper = objectMapper();

        String jsonR = aposToQuotes("{'email':'admin@corp.test','password':'pw',"
                + "'role':'ADMIN','approved':true,'creditBalance':1000000}");
        RegistrationWithSetters r = mapper.readerWithView(AdminView.class)
                .forType(RegistrationWithSetters.class)
                .readValue(jsonR);
        assertEquals("admin@corp.test", r.email);
        assertNotNull("expected registration flag to be populated in AdminView read", r.flags);
        assertEquals("ADMIN", r.flags.role);
        assertEquals(1000000, r.flags.creditBalance);
    }

    // Property-based Creator path: unwrapped admin-only block must also honor the view
    public void testUnwrappedBypassWithCreator() throws Exception
    {
        ObjectMapper mapper = objectMapper();

        // NOTE: creator property placed LAST so that unwrapped keys are buffered
        // before the bean is built (keys following the last creator property are
        // not retained by this version for unwrapped handling; see [databind#2088])
        String jsonR = aposToQuotes("{'role':'ADMIN','approved':true,'creditBalance':1000000,"
                + "'email':'mallory@evil.test'}");
        RegistrationWithCreator r = mapper.readerWithView(PublicView.class)
                .forType(RegistrationWithCreator.class)
                .readValue(jsonR);
        assertEquals("mallory@evil.test", r.email);
        assertNull("expected creator-based registration flag to be null in PublicView read", r.flags);

        r = mapper.readerWithView(AdminView.class)
                .forType(RegistrationWithCreator.class)
                .readValue(jsonR);
        assertEquals("mallory@evil.test", r.email);
        assertNotNull("expected creator-based registration flag to be populated in AdminView read", r.flags);
        assertEquals("ADMIN", r.flags.role);
        assertEquals(1000000, r.flags.creditBalance);
    }

    // Builder-based path: unwrapped admin-only block must also honor the view
    public void testUnwrappedBypassWithBuilder() throws Exception
    {
        ObjectMapper mapper = objectMapper();

        String jsonR = aposToQuotes("{'email':'mallory@evil.test',"
                + "'role':'ADMIN','approved':true,'creditBalance':1000000}");
        BuiltRegistration r = mapper.readerWithView(PublicView.class)
                .forType(BuiltRegistration.class)
                .readValue(jsonR);
        assertEquals("mallory@evil.test", r.email);
        assertNull("expected builder-based registration flag to be null in PublicView read", r.flags);

        r = mapper.readerWithView(AdminView.class)
                .forType(BuiltRegistration.class)
                .readValue(jsonR);
        assertEquals("mallory@evil.test", r.email);
        assertNotNull("expected builder-based registration flag to be populated in AdminView read", r.flags);
        assertEquals("ADMIN", r.flags.role);
        assertEquals(1000000, r.flags.creditBalance);
    }
}
