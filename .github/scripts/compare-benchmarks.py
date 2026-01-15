#!/usr/bin/env python3
"""
Compare JMH benchmark results against baseline.
Detects performance regressions and generates markdown report.
"""

import json
import sys
from typing import Dict, List, Tuple
from dataclasses import dataclass


@dataclass
class BenchmarkResult:
    """Single benchmark result"""
    name: str
    params: Dict[str, str]
    score: float
    error: float
    unit: str
    
    def get_key(self) -> str:
        """Generate unique key for benchmark + params"""
        param_str = ",".join(f"{k}={v}" for k, v in sorted(self.params.items()))
        return f"{self.name}({param_str})"


def parse_jmh_results(filepath: str) -> List[BenchmarkResult]:
    """Parse JMH JSON output"""
    with open(filepath, 'r') as f:
        data = json.load(f)
    
    results = []
    for entry in data:
        benchmark = entry.get('benchmark', '')
        primary_metric = entry.get('primaryMetric', {})
        
        result = BenchmarkResult(
            name=benchmark,
            params=entry.get('params', {}),
            score=primary_metric.get('score', 0.0),
            error=primary_metric.get('scoreError', 0.0),
            unit=primary_metric.get('scoreUnit', 'ms/op')
        )
        results.append(result)
    
    return results


def compare_results(baseline: List[BenchmarkResult], 
                   current: List[BenchmarkResult],
                   threshold: float = 0.20) -> Tuple[List[Tuple], List[Tuple], List[Tuple]]:
    """
    Compare baseline vs current results.
    
    Returns:
        (regressions, improvements, unchanged)
    """
    baseline_map = {r.get_key(): r for r in baseline}
    current_map = {r.get_key(): r for r in current}
    
    regressions = []
    improvements = []
    unchanged = []
    
    for key, curr in current_map.items():
        if key not in baseline_map:
            # New benchmark - no comparison
            continue
        
        base = baseline_map[key]
        
        # Calculate percentage change
        delta = ((curr.score - base.score) / base.score) * 100
        
        if abs(delta) < 5.0:  # Within 5% - consider unchanged
            unchanged.append((base, curr, delta))
        elif delta > threshold * 100:  # Regression (slower)
            regressions.append((base, curr, delta))
        elif delta < -5.0:  # Improvement (faster)
            improvements.append((base, curr, delta))
        else:
            unchanged.append((base, curr, delta))
    
    return regressions, improvements, unchanged


def format_benchmark_name(result: BenchmarkResult) -> str:
    """Format benchmark name with params for display"""
    class_name = result.name.split('.')[-1]
    method = class_name.split('.')[-1] if '.' in class_name else class_name
    
    params = ", ".join(f"{k}={v}" for k, v in sorted(result.params.items()))
    return f"`{method}({params})`"


def generate_report(regressions: List[Tuple], 
                   improvements: List[Tuple], 
                   unchanged: List[Tuple]) -> str:
    """Generate markdown report"""
    report = []
    
    # Summary
    total = len(regressions) + len(improvements) + len(unchanged)
    report.append(f"**Total Benchmarks:** {total}")
    report.append(f"- ⚠️ Regressions: {len(regressions)}")
    report.append(f"- ✅ Improvements: {len(improvements)}")
    report.append(f"- ➖ Unchanged: {len(unchanged)}")
    report.append("")
    
    # Regressions (critical)
    if regressions:
        report.append("### ⚠️ REGRESSION DETECTED")
        report.append("")
        report.append("| Benchmark | Baseline | Current | Change |")
        report.append("|-----------|----------|---------|--------|")
        
        for base, curr, delta in sorted(regressions, key=lambda x: x[2], reverse=True):
            name = format_benchmark_name(curr)
            baseline_str = f"{base.score:.2f}ms"
            current_str = f"{curr.score:.2f}ms"
            change_str = f"🔴 +{delta:.1f}%"
            report.append(f"| {name} | {baseline_str} | {current_str} | {change_str} |")
        
        report.append("")
        report.append("**Action Required:** Investigate performance regression before merging.")
        report.append("")
    
    # Improvements (celebrate!)
    if improvements:
        report.append("### ✅ Performance Improvements")
        report.append("")
        report.append("| Benchmark | Baseline | Current | Change |")
        report.append("|-----------|----------|---------|--------|")
        
        for base, curr, delta in sorted(improvements, key=lambda x: x[2]):
            name = format_benchmark_name(curr)
            baseline_str = f"{base.score:.2f}ms"
            current_str = f"{curr.score:.2f}ms"
            change_str = f"🟢 {delta:.1f}%"
            report.append(f"| {name} | {baseline_str} | {current_str} | {change_str} |")
        
        report.append("")
    
    # Stable performance
    if unchanged:
        report.append("<details>")
        report.append("<summary>📊 Stable Performance (click to expand)</summary>")
        report.append("")
        report.append("| Benchmark | Baseline | Current | Change |")
        report.append("|-----------|----------|---------|--------|")
        
        for base, curr, delta in unchanged:
            name = format_benchmark_name(curr)
            baseline_str = f"{base.score:.2f}ms"
            current_str = f"{curr.score:.2f}ms"
            change_str = f"➖ {delta:+.1f}%"
            report.append(f"| {name} | {baseline_str} | {current_str} | {change_str} |")
        
        report.append("")
        report.append("</details>")
        report.append("")
    
    # Thresholds
    report.append("---")
    report.append("**Regression Threshold:** >20% slower")
    report.append("**Stability Range:** ±5%")
    
    return "\n".join(report)


def main():
    if len(sys.argv) != 3:
        print("Usage: compare-benchmarks.py <baseline.json> <current.json>")
        sys.exit(1)
    
    baseline_file = sys.argv[1]
    current_file = sys.argv[2]
    
    try:
        baseline_results = parse_jmh_results(baseline_file)
        current_results = parse_jmh_results(current_file)
        
        regressions, improvements, unchanged = compare_results(
            baseline_results, 
            current_results,
            threshold=0.20  # 20% regression threshold
        )
        
        report = generate_report(regressions, improvements, unchanged)
        print(report)
        
        # Exit with error if regressions detected
        if regressions:
            sys.exit(1)
        
    except FileNotFoundError as e:
        print(f"Error: {e}")
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"Error parsing JSON: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()
