package com.zeticai.t5grammar

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class Tokenizer(context: Context) {
    private val tokenToId = HashMap<String, Int>()
    private val idToToken = HashMap<Int, String>()

    init {
        loadVocab(context)
    }

    private fun loadVocab(context: Context) {
        try {
            val inputStream = context.assets.open("t5_vocab.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()

            val json = JSONObject(sb.toString())
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next() // ID as String
                val token = json.getString(key)
                val id = key.toInt()
                
                tokenToId[token] = id
                idToToken[id] = token
            }
            Log.d("Tokenizer", "Loaded ${tokenToId.size} tokens.")
        } catch (e: Exception) {
            Log.e("Tokenizer", "Failed to load vocab", e)
        }
    }

    // "Split & Match" Strategy matching Swift implementation
    fun encode(text: String): LongArray {
        val ids = ArrayList<Long>()
        
        // 1. Split by space
        val words = text.split(" ")
        
        for ((i, word) in words.withIndex()) {
            if (word.isEmpty()) continue
            
            var tokenString = word
            // T5: Prepend Lower One Eighth Block to non-start words
            if (i > 0) {
                tokenString = "\u2581" + tokenString
            }
            
            // Greedy Match
            var remaining = tokenString
            while (remaining.isNotEmpty()) {
                var matchFound = false
                
                // Optimization: Check full string first
                if (tokenToId.containsKey(remaining)) {
                    ids.add(tokenToId[remaining]!!.toLong())
                    remaining = ""
                    matchFound = true
                } else {
                    // Check prefixes
                    val maxLen = remaining.length
                    for (len in maxLen downTo 1) {
                        val prefix = remaining.substring(0, len)
                        if (tokenToId.containsKey(prefix)) {
                            ids.add(tokenToId[prefix]!!.toLong())
                            remaining = remaining.substring(len)
                            matchFound = true
                            break
                        }
                    }
                }
                
                if (!matchFound) {
                    // UNK
                    remaining = remaining.substring(1)
                    ids.add(2) // <unk>
                }
            }
        }
        
        ids.add(1) // EOS
        return ids.toLongArray()
    }

    fun decode(tokens: LongArray): String {
        val sb = StringBuilder()
        for (token in tokens) {
            if (token == 1L) break // EOS
            
            val tokenStr = idToToken[token.toInt()]
            if (tokenStr != null) {
                // T5 Tokenizer uses U+2581 (Lower One Eighth Block) for spaces
                // We replace it with a normal space.
                var cleanToken = tokenStr.replace("\u2581", " ")
                
                // Fallback: Check for literal escaped unicode if JSON parser didn't unescape
                if (cleanToken.contains("\\u2581")) {
                     cleanToken = cleanToken.replace("\\u2581", " ")
                }
                
                sb.append(cleanToken)
            }
        }
        
        var result = sb.toString().trim()
        
        // Post-Processing for Punctuation (Common in T5 detokenization)
        // Fix "word , word" -> "word, word"
        // Fix "word ." -> "word."
        result = result.replace(" ,", ",")
            .replace(" .", ".")
            .replace(" !", "!")
            .replace(" ?", "?")
            .replace(" '", "'") // Fix "don 't" -> "don't" tokens
            .replace(" `", "'") // Fix "don `t" -> "don't" artifacts
            
        // Reduce double spaces if any
        result = result.replace("  ", " ")
            
        return result
    }
}
