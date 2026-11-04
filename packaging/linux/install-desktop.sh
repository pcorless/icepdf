#!/usr/bin/env bash
# Installs the icepdf viewer desktop entry, icon and launcher for the current user.
#
#   ./packaging/linux/install-desktop.sh [path/to/lib/folder]                  the JavaFX viewer (default)
#   ./packaging/linux/install-desktop.sh --swing [path/to/icepdf-viewer-*.jar] the Swing viewer
#
# Without a path the JavaFX viewer comes from viewer/viewer-fx/build/launcher/lib (build it with
# ./gradlew :viewer:viewer-fx:launcherLibs), and the Swing viewer from the most recent jar under
# viewer/viewer-awt/build/libs.  --fx is still accepted and means the default.
# Pass --uninstall to remove everything this script installed, both viewers.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HERE="$REPO_ROOT/packaging/linux"

BIN_DIR="$HOME/.local/bin"
LIB_DIR="$HOME/.local/lib/icepdf"
APP_DIR="$HOME/.local/share/applications"
ICON_ROOT="$HOME/.local/share/icons/hicolor"
ICON_SIZES="16 24 32 48 64 128 256"
FX_LIB_DIR="$HOME/.local/lib/icepdf-fx"

# install_icons <images folder> <icon name>: every PNG size plus the scalable SVG into hicolor
install_icons() {
    for size in $ICON_SIZES; do
        local src="$1/icepdf-app-icon-${size}x${size}.png"
        [ -f "$src" ] || { echo "Missing icon: $src" >&2; exit 1; }
        install -D -m 0644 "$src" "$ICON_ROOT/${size}x${size}/apps/$2.png"
    done
    install -D -m 0644 "$HERE/icepdf.svg" "$ICON_ROOT/scalable/apps/$2.svg"
}

if [ "${1:-}" = "--uninstall" ]; then
    rm -f "$BIN_DIR/icepdf-viewer" \
          "$APP_DIR/icepdf-viewer.desktop" \
          "$BIN_DIR/icepdf-viewer-fx" \
          "$APP_DIR/icepdf-viewer-fx.desktop"
    for name in icepdf-viewer icepdf-viewer-fx; do
        for size in $ICON_SIZES; do rm -f "$ICON_ROOT/${size}x${size}/apps/$name.png"; done
        rm -f "$ICON_ROOT/scalable/apps/$name.svg"
    done
    rm -rf "$LIB_DIR" "$FX_LIB_DIR"
    update-desktop-database "$APP_DIR" 2>/dev/null || true
    gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
    echo "Removed icepdf desktop entry."
    exit 0
fi

MODE=fx
case "${1:-}" in
    --swing) MODE=swing; shift ;;
    --fx) shift ;;
esac

if [ "$MODE" = "fx" ]; then
    SRC="${1:-$REPO_ROOT/viewer/viewer-fx/build/launcher/lib}"
    if ! ls "$SRC"/icepdf-viewer-fx-*.jar >/dev/null 2>&1; then
        echo "No JavaFX viewer libraries in $SRC. Build them first:" >&2
        echo "  ./gradlew :viewer:viewer-fx:launcherLibs" >&2
        exit 1
    fi
    mkdir -p "$BIN_DIR" "$APP_DIR"
    rm -rf "$FX_LIB_DIR" && mkdir -p "$FX_LIB_DIR"
    install -m 0644 "$SRC"/*.jar "$FX_LIB_DIR/"
    install_icons "$REPO_ROOT/viewer/viewer-fx/src/main/resources/org/icepdf/fx/images" icepdf-viewer-fx
    install -m 0755 "$HERE/icepdf-viewer-fx" "$BIN_DIR/icepdf-viewer-fx"
    install -m 0644 "$HERE/icepdf-viewer-fx.desktop" "$APP_DIR/icepdf-viewer-fx.desktop"

    case ":$PATH:" in
        *":$BIN_DIR:"*) ;;
        *)  sed -i "s|^Exec=icepdf-viewer-fx|Exec=$BIN_DIR/icepdf-viewer-fx|; s|^TryExec=icepdf-viewer-fx|TryExec=$BIN_DIR/icepdf-viewer-fx|" \
                "$APP_DIR/icepdf-viewer-fx.desktop"
            echo "Note: $BIN_DIR is not on your PATH; desktop entry uses the absolute path." ;;
    esac

    update-desktop-database "$APP_DIR" 2>/dev/null || true
    gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
    desktop-file-validate "$APP_DIR/icepdf-viewer-fx.desktop" 2>/dev/null || true

    echo "Installed:"
    echo "  libs     $FX_LIB_DIR"
    echo "  launcher $BIN_DIR/icepdf-viewer-fx"
    echo "  desktop  $APP_DIR/icepdf-viewer-fx.desktop"
    echo "  icons    $ICON_ROOT/*/apps/icepdf-viewer-fx.*"
    exit 0
fi

JAR="${1:-}"
if [ -z "$JAR" ]; then
    JAR=$(ls -1 "$REPO_ROOT"/viewer/viewer-awt/build/libs/icepdf-viewer-*.jar 2>/dev/null \
          | grep -v -- '-sources' | sort -V | tail -n1 || true)
fi
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
    echo "No viewer jar found. Build one first:" >&2
    echo "  ./gradlew :viewer:viewer-awt:assemble   (then run with --swing)" >&2
    exit 1
fi

mkdir -p "$BIN_DIR" "$LIB_DIR" "$APP_DIR"

install -m 0644 "$JAR" "$LIB_DIR/$(basename "$JAR")"
install_icons "$REPO_ROOT/viewer/viewer-awt/src/main/resources/org/icepdf/ri/images" icepdf-viewer
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
echo "  icons    $ICON_ROOT/*/apps/icepdf-viewer.*"
