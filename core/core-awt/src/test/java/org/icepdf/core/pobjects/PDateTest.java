/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.pobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link PDate}, the PDF date string.
 * <p>
 * The format is {@code D:YYYYMMDDHHmmSSOHH'mm'} and everything after the year is optional, so a
 * real document offers every prefix of it: a bare year, a date with no time, a time with no zone.
 * Each field is a fixed-width slice of the string, which makes a truncated date the interesting
 * case - stopping one field too late reads the next field's digits, or runs off the end.
 */
public class PDateTest {

    // ------------------------------------------------------------------
    // a complete date
    // ------------------------------------------------------------------

    @DisplayName("a full date is split into its fields")
    @Test
    public void fullDate() {
        PDate date = new PDate("D:20260912143005-05'00'");
        assertEquals("2026", date.getYear());
        assertEquals("09", date.getMonth());
        assertEquals("12", date.getDay());
        assertEquals("14", date.getHour());
        assertEquals("30", date.getMinute());
        assertEquals("05", date.getSecond());
        assertEquals("05", date.getTimeZoneHour());
        assertEquals("00", date.getTimeZoneMinute());
    }

    @DisplayName("the sign of the time zone offset is kept")
    @Test
    public void timeZoneSign() {
        // Getting this backwards moves a timestamp by twice the offset.
        assertFalse(new PDate("D:20260912143005-05'00'").getTimeZoneOffset(),
                "a minus offset is behind UTC");
        assertTrue(new PDate("D:20260912143005+05'30'").getTimeZoneOffset(),
                "a plus offset is ahead of UTC");
    }

    @DisplayName("a date with no D: prefix is still parsed")
    @Test
    public void withoutPrefix() {
        // The prefix is required by the specification and omitted by plenty of producers.
        PDate date = new PDate("20260912143005");
        assertEquals("2026", date.getYear());
        assertEquals("09", date.getMonth());
        assertEquals("12", date.getDay());
    }

    // ------------------------------------------------------------------
    // truncated dates
    // ------------------------------------------------------------------

    @DisplayName("a date truncated at any field parses as far as it goes")
    @Test
    public void truncatedDates() {
        assertEquals("2026", new PDate("D:2026").getYear());

        PDate toMonth = new PDate("D:202609");
        assertEquals("2026", toMonth.getYear());
        assertEquals("09", toMonth.getMonth());

        PDate toDay = new PDate("D:20260912");
        assertEquals("12", toDay.getDay());

        PDate toHour = new PDate("D:2026091214");
        assertEquals("14", toHour.getHour());

        PDate toMinute = new PDate("D:202609121430");
        assertEquals("30", toMinute.getMinute());

        PDate toSecond = new PDate("D:20260912143005");
        assertEquals("05", toSecond.getSecond());
    }

    @DisplayName("a date with no time zone leaves the zone fields at their defaults")
    @Test
    public void noTimeZone() {
        PDate date = new PDate("D:20260912143005");
        assertNotNull(date.getTimeZoneHour());
        assertNotNull(date.getTimeZoneMinute());
    }

    @DisplayName("a Z time zone means UTC")
    @Test
    public void utcTimeZone() {
        PDate date = new PDate("D:20260912143005Z");
        assertEquals("2026", date.getYear());
        assertEquals("05", date.getSecond());
    }

    @DisplayName("an unparseable date does not throw")
    @Test
    public void malformedDate() {
        // Dates are cosmetic; one the library cannot read must not stop a document opening.
        assertNotNull(new PDate("not a date at all"));
        assertNotNull(new PDate(""));
        assertNotNull(new PDate(null));
    }

    // ------------------------------------------------------------------
    // conversions
    // ------------------------------------------------------------------

    @DisplayName("a full date converts to the same instant as a LocalDateTime")
    @Test
    public void asLocalDateTime() {
        LocalDateTime dateTime = new PDate("D:20260912143005-05'00'").asLocalDateTime();
        assertEquals(2026, dateTime.getYear());
        assertEquals(9, dateTime.getMonthValue());
        assertEquals(12, dateTime.getDayOfMonth());
        assertEquals(14, dateTime.getHour());
        assertEquals(30, dateTime.getMinute());
        assertEquals(5, dateTime.getSecond());
    }

    @DisplayName("a date with a zone converts to a java Date")
    @Test
    public void asDateWithTimeZone() {
        assertNotNull(new PDate("D:20260912143005-05'00'").asDateWithTimeZone());
    }

    @DisplayName("a date built from a java Date round trips back to the same day")
    @Test
    public void createDate() {
        // This is what the library writes into /ModDate when an annotation is edited.
        PDate created = PDate.createDate(new Date());
        assertNotNull(created.getYear());
        assertEquals(4, created.getYear().length());
        assertTrue(created.equalsDay(created));
    }

    @DisplayName("two dates on the same day compare equal by day, whatever their times")
    @Test
    public void equalsDay() {
        PDate morning = new PDate("D:20260912083000");
        PDate evening = new PDate("D:20260912203000");
        PDate tomorrow = new PDate("D:20260913083000");
        assertTrue(morning.equalsDay(evening));
        assertFalse(morning.equalsDay(tomorrow));
    }

    @DisplayName("toString renders a readable date rather than the raw string")
    @Test
    public void readableToString() {
        String rendered = new PDate("D:20260912143005-05'00'").toString();
        assertNotNull(rendered);
        assertTrue(rendered.contains("2026"), rendered);
    }

    @DisplayName("a date formats without a time zone as well as with one")
    @Test
    public void formatDateTime() {
        assertNotNull(PDate.formatDateTime(new Date()));
        assertNotNull(PDate.formatDateTime(new Date(), java.util.TimeZone.getTimeZone("UTC")));
    }
}
