package com.zeticai.medasr

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset

class CTCDecoder(vocabInfo: String) {
    // Array map: index -> character/string
    private var idToToken: Array<String> = emptyArray()
    private val blankIndex = 0 // Usually 0 is blank in HF CTC models

    init {
        loadVocab(vocabInfo)
    }

    private fun loadVocab(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val tempVocab = mutableMapOf<Int, String>()
            var maxId = 0

            // 1. Load "added_tokens" (Special tokens like <s>, </s>, <pad>)
            if (json.has("added_tokens")) {
                val addedTokens = json.getJSONArray("added_tokens")
                for (i in 0 until addedTokens.length()) {
                    val tokenObj = addedTokens.getJSONObject(i)
                    if (tokenObj.has("id") && tokenObj.has("content")) {
                        val id = tokenObj.getInt("id")
                        val content = tokenObj.getString("content")
                        tempVocab[id] = replaceSpecialChars(content)
                        if (id > maxId) maxId = id
                    }
                }
                android.util.Log.d("MedASR", "CTCDecoder parsed ${addedTokens.length()} added_tokens.")
            }

            // 2. Load "model.vocab" (Main tokens)
            if (json.has("model")) {
                val model = json.getJSONObject("model")
                if (model.has("vocab")) {
                    val vocabObj = model.get("vocab")
                    
                    if (vocabObj is org.json.JSONArray) {
                        // Unigram format: vocab is [ ["token", score], ... ]
                        for (i in 0 until vocabObj.length()) {
                            val entry = vocabObj.getJSONArray(i)
                            val token = entry.getString(0)
                            tempVocab[i] = replaceSpecialChars(token)
                            if (i > maxId) maxId = i
                        }
                        android.util.Log.d("MedASR", "CTCDecoder parsed ${vocabObj.length()} entries (Unigram Array).")
                    } else if (vocabObj is JSONObject) {
                        // BPE/WordPiece format: vocab is { "token": id }
                         val keys = vocabObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val index = vocabObj.getInt(key)
                            tempVocab[index] = replaceSpecialChars(key)
                            if (index > maxId) maxId = index
                        }
                        android.util.Log.d("MedASR", "CTCDecoder parsed entries (Map).")
                    }
                }
            } else {
                 // Fallback: Simple vocab.json map at top level
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (json.get(key) is Int) {
                        val index = json.getInt(key)
                        tempVocab[index] = replaceSpecialChars(key)
                        if (index > maxId) maxId = index
                    }
                }
                android.util.Log.d("MedASR", "CTCDecoder parsed entries (Fallback Map).")
            }
            
            // Build Dense Array
            // Ensure size covers the maxId found.
            // processor_config says 613, but let's be dynamic based on what we see.
            val arraySize = maxId + 1
            idToToken = Array(arraySize) { "" }
            
            for ((id, token) in tempVocab) {
                if (id in idToToken.indices) {
                    idToToken[id] = token
                }
            }
            
            // Validation
            val missingCount = idToToken.count { it.isEmpty() && it != "" } // Accessing empty string is fine if mapped, check coverage?
            // Wait, we initialize with "", so "missing" is indistinguishable from "mapped to empty" (like special tokens).
            // Let's use a separate logic or simple check.
            // Actually, mapped special tokens return "" from replaceSpecialChars.
            // So checking for empty isn't strictly "missing".
            // But let's check explicit keys vs array size.
            
            val populatedCount = tempVocab.size
            val size = idToToken.size
            val reallyMissing = size - populatedCount // Rough metric if IDs are contiguous
            
            android.util.Log.d("MedASR", "CTCDecoder Finalized: Size=$size, ParsedItems=$populatedCount")
            
            // Log missing range if any (simple scan for holes if needed, but not critical if size matches roughly)
            
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("MedASR", "Error loading vocab", e)
        }
    }

    private fun replaceSpecialChars(char: String): String {
        // Replace HF special characters like | with space if needed
        return when (char) {
            "|", "<pad>", "<s>", "</s>", "<epsilon>" -> ""  // Ignore special tokens (Empty string)
            "▁" -> " " // Metaspace to space
            "[UNK]", "<unk>" -> "?"
            else -> char.replace("▁", " ") // Handle "▁the" -> " the"
        }
    }

    fun decode(tokenIds: IntArray): String {
        // Greedy Decode:
        // 1. Merge repeats
        // 2. Remove blanks
        val sb = StringBuilder()
        var lastIndex = -1
        
        // Debug: Check if we have OutOfBounds potential
        // Log.d("MedASR", "Decoding ${tokenIds.size} tokens with vocab size ${idToToken.size}")

        for (id in tokenIds) {
            // Validate ID
            if (id < 0 || id >= idToToken.size) {
                 // Log.w("MedASR", "Token ID $id out of bounds (Size: ${idToToken.size})")
                 continue
            }

            if (id != lastIndex) {
                 if (id != blankIndex) {
                     val char = idToToken[id]
                     sb.append(char)
                 }
            }
            lastIndex = id
        }
        
        // Post-Processing for MedASR
        var text = sb.toString()
        text = text.replace("{period}", ".")
        text = text.replace("{comma}", ",")
        text = text.replace("{colon}", ":")
        text = text.replace("{new paragraph}", "\n")
        // text = text.replace("</s>", "") // Already mapped to "" in replaceSpecialChars
        
        return text.trim()
    }
    fun getVocabSize(): Int {
        return idToToken.size
    }
}
