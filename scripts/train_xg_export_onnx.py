#!/usr/bin/env python3
"""
Pipeline trenowania modelu xG i eksportu do ONNX / JSON.
Dane wejściowe: CSV wyeksportowany z API (POST /api/v1/export/match-logs, format=csv)
lub syntetyczne dane z cechami: zone, distance_to_goal, is_header, minute, score_diff, pressure_count, goal.

Użycie:
  python scripts/train_xg_export_onnx.py --input events.csv --output-dir data/models
  python scripts/train_xg_export_onnx.py --synthetic 5000 --output-dir data/models
"""
import argparse
import json
import os

def parse_args():
    p = argparse.ArgumentParser(description="Train xG model, export ONNX and/or JSON coefficients")
    p.add_argument("--input", type=str, help="Path to CSV with columns: zone, distance_to_goal, is_header, minute, outcome/xG, goal")
    p.add_argument("--synthetic", type=int, metavar="N", help="Generate N synthetic samples instead of loading CSV")
    p.add_argument("--output-dir", type=str, default="data/models", help="Directory for xg_model.onnx and xg_coefs.json")
    p.add_argument("--format", choices=["onnx", "json", "both"], default="both", help="Export format")
    return p.parse_args()

def load_csv_or_synthetic(path, n_synthetic):
    import numpy as np
    if n_synthetic:
        rng = np.random.default_rng(42)
        n = n_synthetic
        zone = rng.integers(1, 13, size=n)
        distance_to_goal = 8 + (12 - zone) * 4 + rng.uniform(-2, 2, size=n)
        is_header = (rng.random(size=n) < 0.1).astype(np.float64)
        minute = rng.integers(0, 91, size=n)
        score_diff = rng.integers(-3, 4, size=n)
        pressure_count = rng.integers(0, 5, size=n)
        # goal ~ Bernoulli(sigmoid(-0.5 - 0.05*zone - 0.02*distance + 0.2*header + ...))
        logit = -0.5 - 0.05 * zone - 0.02 * distance_to_goal + 0.2 * is_header + 0.01 * minute / 90 + 0.02 * score_diff - 0.05 * pressure_count + rng.normal(0, 0.3, size=n)
        goal = (1 / (1 + np.exp(-logit)) > rng.random(size=n)).astype(np.int64)
        X = np.column_stack([zone, distance_to_goal, is_header, minute / 90.0, score_diff, pressure_count]).astype(np.float64)
        y = goal
        return X, y
    if not path or not os.path.isfile(path):
        raise FileNotFoundError(f"Input file not found: {path}")
    import pandas as pd
    df = pd.read_csv(path)
    # Expect columns: zone, distance_to_goal (or infer), is_header, minute, score_diff, pressure_count, goal or xG/outcome
    for c in ["zone", "minute"]:
        if c not in df.columns:
            raise ValueError(f"CSV must have column: {c}")
    if "distance_to_goal" not in df.columns and "zone" in df.columns:
        df["distance_to_goal"] = 8 + (12 - df["zone"]) * 4
    if "is_header" not in df.columns:
        df["is_header"] = 0.0
    if "score_diff" not in df.columns:
        df["score_diff"] = 0
    if "pressure_count" not in df.columns:
        df["pressure_count"] = 0
    if "goal" not in df.columns:
        if "outcome" in df.columns:
            df["goal"] = (df["outcome"] == "Goal").astype(int)
        elif "xG" in df.columns:
            df["goal"] = (np.random.random(len(df)) < df["xG"].astype(float)).astype(int)
        else:
            raise ValueError("CSV must have 'goal' or 'outcome' or 'xG'")
    X = df[["zone", "distance_to_goal", "is_header", "minute", "score_diff", "pressure_count"]].astype(float)
    X["minute"] = X["minute"] / 90.0
    y = df["goal"].astype(int).values
    return X.values, y

def train_logistic(X, y):
    from sklearn.linear_model import LogisticRegression
    m = LogisticRegression(max_iter=500, random_state=42)
    m.fit(X, y)
    return m

def export_json(model, path: str):
    coefs = model.coef_.flatten().tolist()
    intercept = float(model.intercept_[0])
    obj = {"intercept": intercept, "coefs": coefs}
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
    print(f"Wrote {path}")

def export_onnx(model, path: str, X_sample):
    try:
        from skl2onnx import convert_sklearn
        from skl2onnx.common.data_types import FloatTensorType
        n_features = X_sample.shape[1]
        initial_type = [("float_input", FloatTensorType([None, n_features]))]
        onnx_model = convert_sklearn(model, initial_types=initial_type, target_opset=14)
        with open(path, "wb") as f:
            f.write(onnx_model.SerializeToString())
        print(f"Wrote {path}")
    except ImportError:
        print("Install skl2onnx and onnx to export ONNX: pip install skl2onnx onnx")

def main():
    args = parse_args()
    X, y = load_csv_or_synthetic(args.input, args.synthetic)
    print(f"Training on {len(X)} samples, goal rate = {y.mean():.3f}")
    model = train_logistic(X, y)
    os.makedirs(args.output_dir, exist_ok=True)
    if args.format in ("json", "both"):
        export_json(model, os.path.join(args.output_dir, "xg_coefs.json"))
    if args.format in ("onnx", "both"):
        export_onnx(model, os.path.join(args.output_dir, "xg_model.onnx"), X[:1])

if __name__ == "__main__":
    main()
