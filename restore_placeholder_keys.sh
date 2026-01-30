#!/bin/bash

# Script to manually restore placeholder API keys in key files
# Useful if you want to reset keys back to placeholders

PLACEHOLDER_KEY="YOUR_PERSONAL_ACCESS_TOKEN"

echo "Restoring placeholder keys to: $PLACEHOLDER_KEY"

# Files that contain API keys
KEY_FILES=(
    "apps/t5_base_grammar_correction/Android/app/src/main/java/com/zeticai/t5grammar/T5ModelManager.kt"
    "apps/t5_base_grammar_correction/iOS/T5GrammarCorrection-iOS/T5GrammarCorrection_iOSApp.swift"
    "apps/TextAnonymizer/Android/app/src/main/java/com/zeticai/textanonymizer/Constants.kt"
    "apps/TextAnonymizer/iOS/ZeticMLangeTextAnonymizer-iOS.xcodeproj/xcshareddata/xcschemes/TextAnonymizer.xcscheme"
)

for file in "${KEY_FILES[@]}"; do
    if [ -f "$file" ]; then
        # Restore placeholder keys using the same patterns as adapt_mlange_key.sh
        # Swift files
        if [[ "$file" == *.swift ]]; then
            perl -i -pe 's/(tokenKey|personalKey|privateTokenKey):\s*"[^"]*"/${1}: "'"$PLACEHOLDER_KEY"'"/g' "$file"
            echo "✓ Restored: $file"
        fi
        # Kotlin files
        if [[ "$file" == *.kt ]]; then
            perl -i -pe 's/(MLANGE_PERSONAL_ACCESS_TOKEN\s*=\s*)"[^"]*"/${1}"'"$PLACEHOLDER_KEY"'"/g' "$file"
            perl -i -pe 's/(val\s+tokenKey\s*=\s*)"[^"]*"/${1}"'"$PLACEHOLDER_KEY"'"/g' "$file"
            perl -0777 -i -pe 's/(ZeticMLangeModel\(\s*[^,]+,\s*)"[^"]*"/${1}"'"$PLACEHOLDER_KEY"'"/g' "$file"
            echo "✓ Restored: $file"
        fi
        # Xcode scheme files
        if [[ "$file" == *.xcscheme ]]; then
            perl -i -pe 's/(key = "ZETIC_ACCESS_TOKEN"[^>]*value = ")[^"]*"/${1}"'"$PLACEHOLDER_KEY"'"/g' "$file"
            echo "✓ Restored: $file"
        fi
    else
        echo "⚠ File not found: $file"
    fi
done

echo ""
echo "Placeholder keys restored!"
echo "Note: If you've set up skip-worktree, git will still ignore these changes."
echo "To update your local keys again, run: ./adapt_mlange_key.sh"
