#!/usr/bin/env bash
# Installs the ICEpdf viewer desktop entry, icon and launcher for the current user.
#
#   ./packaging/linux/install-desktop.sh [path/to/icepdf-viewer-*.jar]
#
# Without an argument the most recent jar under viewer/viewer-awt/build/libs is used.
# Pass --uninstall to remove everything this script installed.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HERE="$REPO_ROOT/packaging/linux"

BIN_DIR="$HOME/.local/bin"
LIB_DIR="$HOME/.local/lib/icepdf"
APP_DIR="$HOME/.local/share/applications"
ICON_DIR="$HOME/.local/share/icons/hicolor/64x64/apps"

if [ "${1:-}" = "--uninstall" ]; then
    rm -f "$BIN_DIR/icepdf-viewer" \
          "$APP_DIR/icepdf-viewer.desktop" \
          "$ICON_DIR/icepdf-viewer.png"
    rm -rf "$LIB_DIR"
    update-desktop-database "$APP_DIR" 2>/dev/null || true
    gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
    echo "Removed ICEpdf desktop entry."
    exit 0
fi

JAR="${1:-}"
if [ -z "$JAR" ]; then
    JAR=$(ls -1 "$REPO_ROOT"/viewer/viewer-awt/build/libs/icepdf-viewer-*.jar 2>/dev/null \
          | grep -v -- '-sources' | sort -V | tail -n1 || true)
fi
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
    echo "No viewer jar found. Build one first:" >&2
    echo "  ./gradlew :viewer:viewer-awt:assemble" >&2
    exit 1
fi

ICON_SRC="$REPO_ROOT/viewer/viewer-awt/src/main/resources/org/icepdf/ri/images/icepdf-app-icon-64x64.png"
[ -f "$ICON_SRC" ] || { echo "Missing icon: $ICON_SRC" >&2; exit 1; }

mkdir -p "$BIN_DIR" "$LIB_DIR" "$APP_DIR" "$ICON_DIR"

install -m 0644 "$JAR" "$LIB_DIR/$(basename "$JAR")"
install -m 0644 "$ICON_SRC" "$ICON_DIR/icepdf-viewer.png"
install -m 0755 "$HERE/icepdf-viewer" "$BIN_DIR/icepdf-viewer"
install -m 0644 "$HERE/icepdf-viewer.desktop" "$APP_DIR/icepdf-viewer.desktop"

# GNOME only follows Exec= via PATH; use an absolute path if ~/.local/bin isn't on it.
case ":$PATH:" in
    *":$BIN_DIR:"*) ;;
    *)  sed -i "s|^Exec=icepdf-viewer|Exec=$BIN_DIR/icepdf-viewer|; s|^TryExec=icepdf-viewer|TryExec=$BIN_DIR/icepdf-viewer|" \
            "$APP_DIR/icepdf-viewer.desktop"
        echo "Note: $BIN_DIR is not on your PATH; desktop entry uses the absolute path." ;;
esac

update-desktop-database "$APP_DIR" 2>/dev/null || true
gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
desktop-file-validate "$APP_DIR/icepdf-viewer.desktop" 2>/dev/null || true

echo "Installed:"
echo "  jar     $LIB_DIR/$(basename "$JAR")"
echo "  launcher $BIN_DIR/icepdf-viewer"
echo "  desktop  $APP_DIR/icepdf-viewer.desktop"
echo "  icon     $ICON_DIR/icepdf-viewer.png"
