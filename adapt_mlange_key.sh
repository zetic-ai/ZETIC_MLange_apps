#!/bin/bash

# Information about where to get the key
echo "You can get your ZETIC.MLange Key from https://mlange.zetic.ai"
echo "Please enter your ZETIC.MLange Key:"
read NEW_KEY

if [ -z "$NEW_KEY" ]; then
    echo "Error: Key cannot be empty."
    exit 1
fi

echo "Updating keys to: $NEW_KEY"

# Update iOS files (Swift) - looking for tokenKey: "..." or personalKey: "..."
# Using perl for consistent in-place editing
find apps -name "*.swift" -print0 | xargs -0 perl -i -pe 's/(tokenKey|personalKey):\s*"[^"]*"/${1}: "'"$NEW_KEY"'"/g'

# Update Android files (Kotlin/Java)
# Pattern: ZeticMLangeModel(context, "KEY", ...)
# We use perl -0777 to slurp the whole file and match multi-line patterns
# Regex breakdown:
# (ZeticMLangeModel\(   -> Match constructor start and open paren
# \s*                   -> Optional whitespace/newline
# [^,]+                 -> First argument (context) - greedy match non-commas
# ,                     -> Comma separator
# \s*                   -> Optional whitespace/newline
# )                     -> Capture group $1 (Prefix)
# "[^"]*"               -> The Key String to replace
find apps -name "*.kt" -print0 | xargs -0 perl -0777 -i -pe 's/(ZeticMLangeModel\(\s*[^,]+,\s*)"[^"]*"/${1}"'"$NEW_KEY"'"/g'
find apps -name "*.java" -print0 | xargs -0 perl -0777 -i -pe 's/(ZeticMLangeModel\(\s*[^,]+,\s*)"[^"]*"/${1}"'"$NEW_KEY"'"/g'

echo "Key update complete!"
