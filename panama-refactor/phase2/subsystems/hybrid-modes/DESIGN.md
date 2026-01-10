# Hybrid Modes: Technical Design

## Region-Specific Modes

### Finder Patterns
- Always Mode 2 (8 colors)
- Maximum visibility
- Critical for geometric alignment

### Metadata
- Mode 2-3 (8-16 colors)
- Small volume, needs reliability
- Version, length, mode info

### Data Region
- Use requested mode (3-7)
- High capacity
- LDPC protected

### ECC Region
- Can use any mode
- Redundant data
- Less critical

## Encoding/Decoding

Support multiple palettes simultaneously.
Region boundaries defined in metadata.
Decoder switches modes per region.

See implementation for full specification.
