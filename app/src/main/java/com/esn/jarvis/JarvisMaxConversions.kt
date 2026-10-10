package com.esn.jarvis

import java.util.Locale

/** Forty deterministic offline unit conversions. No exchange rates or network access. */
object JarvisMaxConversions {
    private inline fun convert(input: String, unit: String, conversion: (Double) -> Double): String {
        val value = input.trim().toDoubleOrNull() ?: return "Enter a valid number."
        if (!value.isFinite()) return "Enter a finite number."
        val result = conversion(value)
        if (!result.isFinite()) return "Result is outside supported range."
        return String.format(Locale.US, "%.6f %s", result, unit)
    }

    fun execute(command: String, original: String): String? = when {
            command.startsWith("max km to miles ") -> convert(original.drop("max km to miles ".length), "miles") { value -> value * 0.6213711922 }
            command.startsWith("max miles to km ") -> convert(original.drop("max miles to km ".length), "km") { value -> value * 1.609344 }
            command.startsWith("max meters to feet ") -> convert(original.drop("max meters to feet ".length), "feet") { value -> value * 3.280839895 }
            command.startsWith("max feet to meters ") -> convert(original.drop("max feet to meters ".length), "meters") { value -> value * 0.3048 }
            command.startsWith("max cm to inches ") -> convert(original.drop("max cm to inches ".length), "inches") { value -> value / 2.54 }
            command.startsWith("max inches to cm ") -> convert(original.drop("max inches to cm ".length), "cm") { value -> value * 2.54 }
            command.startsWith("max mm to inches ") -> convert(original.drop("max mm to inches ".length), "inches") { value -> value / 25.4 }
            command.startsWith("max inches to mm ") -> convert(original.drop("max inches to mm ".length), "mm") { value -> value * 25.4 }
            command.startsWith("max kg to pounds ") -> convert(original.drop("max kg to pounds ".length), "pounds") { value -> value * 2.2046226218 }
            command.startsWith("max pounds to kg ") -> convert(original.drop("max pounds to kg ".length), "kg") { value -> value / 2.2046226218 }
            command.startsWith("max grams to ounces ") -> convert(original.drop("max grams to ounces ".length), "ounces") { value -> value / 28.349523125 }
            command.startsWith("max ounces to grams ") -> convert(original.drop("max ounces to grams ".length), "grams") { value -> value * 28.349523125 }
            command.startsWith("max liters to gallons ") -> convert(original.drop("max liters to gallons ".length), "US gallons") { value -> value / 3.785411784 }
            command.startsWith("max gallons to liters ") -> convert(original.drop("max gallons to liters ".length), "liters") { value -> value * 3.785411784 }
            command.startsWith("max ml to cups ") -> convert(original.drop("max ml to cups ".length), "US cups") { value -> value / 236.5882365 }
            command.startsWith("max cups to ml ") -> convert(original.drop("max cups to ml ".length), "ml") { value -> value * 236.5882365 }
            command.startsWith("max c to f ") -> convert(original.drop("max c to f ".length), "°F") { value -> value * 9.0 / 5.0 + 32.0 }
            command.startsWith("max f to c ") -> convert(original.drop("max f to c ".length), "°C") { value -> (value - 32.0) * 5.0 / 9.0 }
            command.startsWith("max c to k ") -> convert(original.drop("max c to k ".length), "K") { value -> value + 273.15 }
            command.startsWith("max k to c ") -> convert(original.drop("max k to c ".length), "°C") { value -> value - 273.15 }
            command.startsWith("max f to k ") -> convert(original.drop("max f to k ".length), "K") { value -> (value - 32.0) * 5.0 / 9.0 + 273.15 }
            command.startsWith("max k to f ") -> convert(original.drop("max k to f ".length), "°F") { value -> (value - 273.15) * 9.0 / 5.0 + 32.0 }
            command.startsWith("max hours to minutes ") -> convert(original.drop("max hours to minutes ".length), "minutes") { value -> value * 60.0 }
            command.startsWith("max minutes to hours ") -> convert(original.drop("max minutes to hours ".length), "hours") { value -> value / 60.0 }
            command.startsWith("max days to hours ") -> convert(original.drop("max days to hours ".length), "hours") { value -> value * 24.0 }
            command.startsWith("max hours to days ") -> convert(original.drop("max hours to days ".length), "days") { value -> value / 24.0 }
            command.startsWith("max weeks to days ") -> convert(original.drop("max weeks to days ".length), "days") { value -> value * 7.0 }
            command.startsWith("max days to weeks ") -> convert(original.drop("max days to weeks ".length), "weeks") { value -> value / 7.0 }
            command.startsWith("max seconds to minutes ") -> convert(original.drop("max seconds to minutes ".length), "minutes") { value -> value / 60.0 }
            command.startsWith("max minutes to seconds ") -> convert(original.drop("max minutes to seconds ".length), "seconds") { value -> value * 60.0 }
            command.startsWith("max bytes to kb ") -> convert(original.drop("max bytes to kb ".length), "KiB") { value -> value / 1024.0 }
            command.startsWith("max kb to bytes ") -> convert(original.drop("max kb to bytes ".length), "bytes") { value -> value * 1024.0 }
            command.startsWith("max mb to gb ") -> convert(original.drop("max mb to gb ".length), "GiB") { value -> value / 1024.0 }
            command.startsWith("max gb to mb ") -> convert(original.drop("max gb to mb ".length), "MiB") { value -> value * 1024.0 }
            command.startsWith("max gb to tb ") -> convert(original.drop("max gb to tb ".length), "TiB") { value -> value / 1024.0 }
            command.startsWith("max tb to gb ") -> convert(original.drop("max tb to gb ".length), "GiB") { value -> value * 1024.0 }
            command.startsWith("max mph to kph ") -> convert(original.drop("max mph to kph ".length), "km/h") { value -> value * 1.609344 }
            command.startsWith("max kph to mph ") -> convert(original.drop("max kph to mph ".length), "mph") { value -> value / 1.609344 }
            command.startsWith("max square feet to sqm ") -> convert(original.drop("max square feet to sqm ".length), "m²") { value -> value * 0.09290304 }
            command.startsWith("max sqm to square feet ") -> convert(original.drop("max sqm to square feet ".length), "ft²") { value -> value / 0.09290304 }
        else -> null
    }
}
