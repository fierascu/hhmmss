#!/bin/bash
# Build script for hhmmss Docker images
# Provides options for standard build or fast rebuild using base image

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_usage() {
    echo "Usage: ./build.sh [OPTIONS]"
    echo ""
    echo "Build hhmmss Docker image with optimizations"
    echo ""
    echo "OPTIONS:"
    echo "  -h, --help           Show this help message"
    echo "  -f, --fast           Use LibreOffice base image for faster builds"
    echo "  -b, --build-base     Build the LibreOffice base image"
    echo "  -s, --standard       Standard build (default)"
    echo ""
    echo "EXAMPLES:"
    echo "  # Standard build (simple, no base image)"
    echo "  ./build.sh"
    echo ""
    echo "  # One-time: Build LibreOffice base image"
    echo "  ./build.sh --build-base"
    echo ""
    echo "  # Fast rebuild using base image (requires --build-base first)"
    echo "  ./build.sh --fast"
    echo ""
}

build_standard() {
    echo -e "${GREEN}Building hhmmss image (standard mode)...${NC}"
    docker build -t hhmmss:latest .
    echo -e "${GREEN}Build complete!${NC}"
    echo "Run with: docker-compose up -d"
}

build_base() {
    echo -e "${GREEN}Building LibreOffice base image...${NC}"
    echo "This is a one-time build that will speed up future rebuilds."
    docker build -f Dockerfile.libreoffice-base -t hhmmss-libreoffice-base:latest .
    echo -e "${GREEN}Base image build complete!${NC}"
    echo "You can now use fast rebuild mode: ./build.sh --fast"
}

build_fast() {
    echo -e "${YELLOW}Checking if LibreOffice base image exists...${NC}"
    if ! docker image inspect hhmmss-libreoffice-base:latest >/dev/null 2>&1; then
        echo -e "${RED}Error: LibreOffice base image not found!${NC}"
        echo "Please build it first with: ./build.sh --build-base"
        exit 1
    fi

    echo -e "${GREEN}Building hhmmss image (fast mode with base image)...${NC}"
    docker build --build-arg BASE_IMAGE=hhmmss-libreoffice-base:latest -t hhmmss:latest .
    echo -e "${GREEN}Fast build complete!${NC}"
    echo "Run with: docker-compose up -d"
}

# Parse command line arguments
MODE="standard"

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        -f|--fast)
            MODE="fast"
            shift
            ;;
        -b|--build-base)
            MODE="base"
            shift
            ;;
        -s|--standard)
            MODE="standard"
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

# Execute the selected build mode
case $MODE in
    standard)
        build_standard
        ;;
    base)
        build_base
        ;;
    fast)
        build_fast
        ;;
esac

echo ""
echo -e "${GREEN}Next steps:${NC}"
echo "  Start:  docker-compose up -d"
echo "  Logs:   docker-compose logs -f"
echo "  Stop:   docker-compose down"
