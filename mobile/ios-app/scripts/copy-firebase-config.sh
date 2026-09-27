#!/bin/sh
set -eu
case "$CONFIGURATION" in
    Debug) firebase_environment="Dev" ;;
    Release) firebase_environment="Prod" ;;
    *) echo "error: Unsupported Firebase configuration: $CONFIGURATION" >&2; exit 1 ;;
esac

if [ -n "$firebase_environment" ]; then
    plist="$SRCROOT/SaqzIOS/Config/$firebase_environment/GoogleService-Info.plist"
    resources="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH"
    if [ ! -f "$plist" ]; then
        if [ "$CONFIGURATION" = "Release" ]; then
            echo "error: Production Firebase configuration is required: $plist" >&2
            exit 1
        fi
        # A reused build directory must not retain a previous cloud configuration.
        rm -f "$resources/GoogleService-Info.plist"
        built_plist="$TARGET_BUILD_DIR/$INFOPLIST_PATH"
        for telemetry_key in FIREBASE_ANALYTICS_COLLECTION_DEACTIVATED FirebaseCrashlyticsCollectionEnabled; do
            /usr/libexec/PlistBuddy -c "Delete :$telemetry_key" "$built_plist" 2>/dev/null || true
        done
        /usr/libexec/PlistBuddy -c "Add :FIREBASE_ANALYTICS_COLLECTION_DEACTIVATED bool true" "$built_plist"
        /usr/libexec/PlistBuddy -c "Add :FirebaseCrashlyticsCollectionEnabled bool false" "$built_plist"
        exit 0
    fi
    for firebase_key in PROJECT_ID API_KEY GCM_SENDER_ID GOOGLE_APP_ID BUNDLE_ID CLIENT_ID REVERSED_CLIENT_ID; do
        firebase_value=$(/usr/libexec/PlistBuddy -c "Print :$firebase_key" "$plist" 2>/dev/null || true)
        if [ -z "$firebase_value" ]; then
            echo "error: Firebase configuration is missing $firebase_key" >&2
            exit 1
        fi
    done
    firebase_api_key=$(/usr/libexec/PlistBuddy -c "Print :API_KEY" "$plist")
    case "$firebase_api_key" in
        *[!A-Za-z0-9_-]*) echo "error: Firebase API_KEY has an invalid format" >&2; exit 1 ;;
        A*) ;;
        *) echo "error: Firebase API_KEY has an invalid format" >&2; exit 1 ;;
    esac
    if [ "${#firebase_api_key}" -ne 39 ]; then
        echo "error: Firebase API_KEY has an invalid format" >&2
        exit 1
    fi
    firebase_bundle=$(/usr/libexec/PlistBuddy -c "Print :BUNDLE_ID" "$plist")
    if [ "$firebase_bundle" != "$PRODUCT_BUNDLE_IDENTIFIER" ]; then
        echo "error: Firebase BUNDLE_ID must match PRODUCT_BUNDLE_IDENTIFIER" >&2
        exit 1
    fi
    if [ "$CONFIGURATION" = "Release" ]; then
        firebase_project=$(/usr/libexec/PlistBuddy -c "Print :PROJECT_ID" "$plist")
        if [ "$firebase_project" = "saqz-local" ] || [ "${SAQZ_ENVIRONMENT:-}" != "prod" ]; then
            echo "error: Release requires the production Firebase environment" >&2
            exit 1
        fi
    fi
    if [ -f "$plist" ]; then
        mkdir -p "$resources"
        cp "$plist" "$resources/GoogleService-Info.plist"
        built_plist="$TARGET_BUILD_DIR/$INFOPLIST_PATH"
        /usr/libexec/PlistBuddy -c "Delete :FIREBASE_ANALYTICS_COLLECTION_DEACTIVATED" "$built_plist" 2>/dev/null || true
        /usr/libexec/PlistBuddy -c "Delete :FirebaseCrashlyticsCollectionEnabled" "$built_plist" 2>/dev/null || true
        client_id=$(/usr/libexec/PlistBuddy -c "Print :CLIENT_ID" "$plist" 2>/dev/null || true)
        reversed_client_id=$(/usr/libexec/PlistBuddy -c "Print :REVERSED_CLIENT_ID" "$plist" 2>/dev/null || true)
        if [ -n "$client_id" ] && [ -n "$reversed_client_id" ]; then
            built_plist="$TARGET_BUILD_DIR/$INFOPLIST_PATH"
            /usr/libexec/PlistBuddy -c "Delete :GIDClientID" "$built_plist" 2>/dev/null || true
            /usr/libexec/PlistBuddy -c "Add :GIDClientID string $client_id" "$built_plist"
            if ! /usr/libexec/PlistBuddy -c "Print :CFBundleURLTypes" "$built_plist" >/dev/null 2>&1; then
              /usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes array" "$built_plist"
            fi
            url_type_count=0
            while /usr/libexec/PlistBuddy -c "Print :CFBundleURLTypes:$url_type_count" "$built_plist" >/dev/null 2>&1; do
              url_type_count=$((url_type_count + 1))
            done
            google_scheme_present=0
            url_type_index=0
            while [ "$url_type_index" -lt "$url_type_count" ]; do
              scheme=$(/usr/libexec/PlistBuddy -c "Print :CFBundleURLTypes:$url_type_index:CFBundleURLSchemes:0" "$built_plist" 2>/dev/null || true)
              if [ "$scheme" = "$reversed_client_id" ]; then google_scheme_present=1; break; fi
              url_type_index=$((url_type_index + 1))
            done
            if [ "$google_scheme_present" -eq 0 ]; then
              /usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:$url_type_count dict" "$built_plist"
              /usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:$url_type_count:CFBundleURLSchemes array" "$built_plist"
              /usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:$url_type_count:CFBundleURLSchemes:0 string $reversed_client_id" "$built_plist"
            fi
        fi
    fi
fi
