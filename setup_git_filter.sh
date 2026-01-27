#!/bin/bash

# Smart Git filter setup for API key protection
# This sets up automatic conversion of API keys to placeholders on commit

echo "🔧 Setting up Git filter for API key protection..."

# Check if we're in a git repository
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "❌ Error: Not in a git repository."
    exit 1
fi

# Create filters directory if it doesn't exist
mkdir -p .git/filters

# Check if filter script exists
if [ ! -f ".git/filters/mlange-key-clean.sh" ]; then
    echo "❌ Error: Filter script not found at .git/filters/mlange-key-clean.sh"
    echo "   Please ensure the filter script exists."
    exit 1
fi

# Make filter script executable
chmod +x .git/filters/mlange-key-clean.sh

# Configure Git filter
echo "📝 Configuring Git filter..."
git config filter.mlange-key-clean.clean '.git/filters/mlange-key-clean.sh'
git config filter.mlange-key-clean.smudge 'cat'
git config filter.mlange-key-clean.required true

if [ $? -eq 0 ]; then
    echo "✅ Git filter configured successfully!"
else
    echo "❌ Error: Failed to configure Git filter."
    echo "   You may need to run this manually:"
    echo "   git config filter.mlange-key-clean.clean '.git/filters/mlange-key-clean.sh'"
    echo "   git config filter.mlange-key-clean.smudge 'cat'"
    echo "   git config filter.mlange-key-clean.required true"
    exit 1
fi

# Apply filter to existing files
echo "🔄 Applying filter to existing files..."
echo "   (This may take a moment...)"

# Apply to all matching files
git ls-files | grep -E '^apps/.*\.(swift|kt|java|xcscheme)$' | while read file; do
    if [ -f "$file" ]; then
        git checkout HEAD -- "$file" 2>/dev/null || true
    fi
done

echo ""
echo "✅ Setup complete!"
echo ""
echo "📖 How it works:"
echo "   - When you commit: API keys → YOUR_PERSONAL_ACCESS_TOKEN (automatic)"
echo "   - In your local files: Real keys remain (for development)"
echo "   - In Git repository: Only placeholders are stored"
echo ""
echo "💡 Usage:"
echo "   1. Set your API key: ./adapt_mlange_key.sh"
echo "   2. Work normally (your local files keep real keys)"
echo "   3. Commit: git add . && git commit -m '...'"
echo "   4. Keys are automatically converted to placeholders!"
echo ""
echo "🔍 Verify:"
echo "   git diff --cached  # See placeholder in staged changes"
echo "   cat apps/.../file.swift | grep tokenKey  # See real key in local file"
