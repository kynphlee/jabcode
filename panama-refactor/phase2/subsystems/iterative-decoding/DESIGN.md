# Iterative Decoding: Technical Design

## Confidence Scoring Algorithm

```c
confidence = (second_nearest_ΔE - nearest_ΔE) / nearest_ΔE

High separation → High confidence
Low separation → Low confidence (ambiguous)
```

## LDPC Feedback Loop

```
1. Decode → bits
2. LDPC check → syndrome
3. If errors: Identify suspect modules
4. Refine low-confidence suspects
5. Repeat until convergence or max iterations
```

## Spatial Context

Use confident neighbors to guide uncertain decisions.
Exploit correlation: nearby modules likely similar colors.

## Convergence Criteria

- LDPC successful (no parity errors)
- OR: No confidence changes between iterations
- OR: Max iterations reached (fail gracefully)

See full implementation specification in code.
