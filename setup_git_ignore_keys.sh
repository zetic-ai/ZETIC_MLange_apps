#!/bin/bash

# Script to set up git to ignore changes to API key files
# This prevents your local API key changes from being committed

echo "Setting up git to ignore changes to API key files..."

# Files that contain API keys and should be ignored
KEY_FILES=(
    "apps/t5_base_grammar_correction/Android/app/src/main/java/com/zeticai/t5grammar/T5ModelManager.kt"
    "apps/t5_base_grammar_correction/iOS/T5GrammarCorrection-iOS/T5GrammarCorrection_iOSApp.swift"
    "apps/TextAnonymizer/Android/app/src/main/java/com/zeticai/textanonymizer/Constants.kt"
    "apps/TextAnonymizer/iOS/ZeticMLangeTextAnonymizer-iOS.xcodeproj/xcshareddata/xcschemes/TextAnonymizer.xcscheme"
)

# Check if we're in a git repository
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "Error: Not in a git repository."
    exit 1
fi

# Set skip-worktree for each file
for file in "${KEY_FILES[@]}"; do
    if [ -f "$file" ]; then
        git update-index --skip-worktree "$file"
        echo "✓ Ignoring changes to: $file"
    else
        echo "⚠ File not found: $file"
    fi
done

echo ""
echo "Setup complete! Git will now ignore changes to these files."
echo ""
echo "To restore tracking (if needed):"
echo "  git update-index --no-skip-worktree <file>"
echo ""
echo "To see which files are being ignored:"
echo "  git ls-files -v | grep '^S'"
