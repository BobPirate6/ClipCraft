#!/bin/bash
echo "Checking Google Authentication Configuration..."
echo "=============================================="
echo ""

# Check google-services.json
if [ -f "app/google-services.json" ]; then
    echo "✅ google-services.json found"
    echo ""
    echo "OAuth clients in google-services.json:"
    echo "--------------------------------------"
    # Extract client info
    grep -E '"client_id"|"client_type"' app/google-services.json | sed 's/^/  /'
else
    echo "❌ google-services.json NOT found in app/ directory"
fi

echo ""
echo "To complete setup:"
echo "1. Run './gradlew signingReport' to get SHA-1"
echo "2. Add SHA-1 to Firebase Console"
echo "3. Enable Google Sign-In in Firebase Authentication"
echo "4. Rebuild project to generate default_web_client_id"