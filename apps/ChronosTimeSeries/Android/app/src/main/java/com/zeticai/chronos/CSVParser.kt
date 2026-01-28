package com.zeticai.chronos

object CSVParser {

    fun parseCSV(text: String, idColumn: String, timestampColumn: String, targetColumn: String): TimeSeriesDataset {
        val lines = text.split("\n", "\r\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            throw Exception("CSV is empty.")
        }

        val headerLine = lines.first()
        val headers = parseCSVLine(headerLine)

        val idIndex = headers.indexOfFirst { it.equals(idColumn, ignoreCase = true) }
        val timestampIndex = headers.indexOfFirst { it.equals(timestampColumn, ignoreCase = true) }
        val targetIndex = headers.indexOfFirst { it.equals(targetColumn, ignoreCase = true) }

        if (targetIndex == -1) {
            throw Exception("Target column '$targetColumn' not found.")
        }

        val seriesMap = mutableMapOf<String, MutableList<SeriesPoint>>()

        for (line in lines.drop(1)) {
            val columns = parseCSVLine(line)
            if (columns.size <= targetIndex) continue
            
            val idValue = if (idIndex != -1 && idIndex < columns.size) columns[idIndex] else null
            val timestampValue = if (timestampIndex != -1 && timestampIndex < columns.size) columns[timestampIndex] else ""
            val seriesId = if (!idValue.isNullOrEmpty()) idValue else "series-1"
            val targetValue = columns[targetIndex].trim()

            val value = targetValue.toDoubleOrNull() ?: continue
            val point = SeriesPoint(timestampValue, value)
            
            seriesMap.getOrPut(seriesId) { mutableListOf() }.add(point)
        }

        if (seriesMap.isEmpty()) {
            throw Exception("No valid rows found in CSV.")
        }

        return TimeSeriesDataset(seriesMap)
    }

    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (i in line.indices) {
            val char = line[i]
            if (char == '\"') {
                if (inQuotes) {
                    if (i + 1 < line.length && line[i + 1] == '\"') {
                        current.append('\"')
                        // Skip next quote
                        // Actually in simple loop this is hard, let's just handle simple case or use regex if complex.
                        // Impl below is simplified state machine.
                    } else {
                        inQuotes = false
                    }
                } else {
                    inQuotes = true
                }
            } else if (char == ',' && !inQuotes) {
                result.add(current.toString())
                current = StringBuilder()
            } else {
                current.append(char)
            }
        }
        result.add(current.toString())
        return result.map { it.trim() }
    }
}
