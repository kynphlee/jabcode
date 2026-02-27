#!/usr/bin/env python3
"""
TensorFlow POC: Color Classifier for JABCode High-Color Modes

This trains a small MLP to classify camera-captured RGB values back to
palette indices, handling non-linear color shifts that simple K-d tree
lookup cannot.

Target: 128-color mode (8×4×4 distribution) - the hardest case.
"""

import numpy as np
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
import matplotlib.pyplot as plt

# JABCode palette generation (matches encoder.c genColorPalette)
def generate_jabcode_palette(color_count):
    """Generate JABCode palette matching ISO-IEC-23634 spec."""
    if color_count == 8:
        return np.array([
            [0, 0, 0], [0, 0, 255], [0, 255, 0], [0, 255, 255],
            [255, 0, 0], [255, 0, 255], [255, 255, 0], [255, 255, 255]
        ], dtype=np.float32)
    
    distributions = {
        16: (4, 2, 2),
        32: (4, 4, 2),
        64: (4, 4, 4),
        128: (8, 4, 4),
        256: (8, 8, 4)
    }
    
    vr, vg, vb = distributions[color_count]
    
    # Special case for 3 intervals = 85, else 256/(v-1)
    dr = 85.0 if (vr - 1) == 3 else 256.0 / (vr - 1)
    dg = 85.0 if (vg - 1) == 3 else 256.0 / (vg - 1)
    db = 85.0 if (vb - 1) == 3 else 256.0 / (vb - 1)
    
    palette = []
    for i in range(vr):
        r = min(int(dr * i), 255)
        for j in range(vg):
            g = min(int(dg * j), 255)
            for k in range(vb):
                b = min(int(db * k), 255)
                palette.append([r, g, b])
    
    return np.array(palette, dtype=np.float32)


def simulate_camera_capture(rgb, brightness=1.0, saturation=1.0, gamma=1.0, noise_std=10.0):
    """Simulate camera color distortion with various parameters."""
    # Apply brightness
    rgb = rgb * brightness
    
    # Apply saturation (toward gray)
    gray = np.mean(rgb, axis=-1, keepdims=True)
    rgb = gray + saturation * (rgb - gray)
    
    # Apply gamma
    rgb = np.clip(rgb / 255.0, 0, 1)
    rgb = np.power(rgb, gamma) * 255.0
    
    # Add noise
    noise = np.random.normal(0, noise_std, rgb.shape)
    rgb = rgb + noise
    
    return np.clip(rgb, 0, 255)


def generate_training_data(palette, samples_per_color=500):
    """Generate synthetic training data with camera augmentations."""
    X = []
    y = []
    
    for idx, color in enumerate(palette):
        for _ in range(samples_per_color):
            # Random camera parameters
            brightness = np.random.uniform(0.85, 1.15)
            saturation = np.random.uniform(0.80, 1.10)
            gamma = np.random.uniform(0.8, 1.3)
            noise_std = np.random.uniform(5, 20)
            
            # Per-channel shift (simulates camera white balance issues)
            channel_shift = np.random.uniform(-15, 15, 3)
            
            # Generate distorted sample
            sample = simulate_camera_capture(
                color.copy() + channel_shift,
                brightness, saturation, gamma, noise_std
            )
            
            X.append(sample)
            y.append(idx)
    
    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32)


def build_model(num_classes):
    """Build a small MLP classifier."""
    model = keras.Sequential([
        layers.Input(shape=(3,)),
        # Normalize input to [0, 1]
        layers.Lambda(lambda x: x / 255.0),
        # Small MLP - designed to be tiny for mobile
        layers.Dense(64, activation='relu'),
        layers.BatchNormalization(),
        layers.Dropout(0.2),
        layers.Dense(128, activation='relu'),
        layers.BatchNormalization(),
        layers.Dropout(0.2),
        layers.Dense(64, activation='relu'),
        layers.Dense(num_classes, activation='softmax')
    ])
    
    model.compile(
        optimizer=keras.optimizers.Adam(learning_rate=0.001),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    
    return model


def evaluate_by_color_mode(model, color_counts=[8, 16, 32, 64, 128]):
    """Evaluate model accuracy for each color mode."""
    print("\n" + "="*60)
    print("Evaluation by Color Mode")
    print("="*60)
    
    results = {}
    
    for nc in color_counts:
        palette = generate_jabcode_palette(nc)
        
        # Generate test data with moderate camera shift
        X_test, y_test = generate_training_data(palette, samples_per_color=100)
        
        # Predict
        predictions = np.argmax(model.predict(X_test, verbose=0), axis=1)
        accuracy = np.mean(predictions == y_test) * 100
        
        results[nc] = accuracy
        print(f"  {nc:3d}-color mode: {accuracy:5.1f}% accuracy")
    
    return results


def train_per_mode_models():
    """Train separate models for each color mode and compare."""
    print("\n" + "="*60)
    print("Training Per-Color-Mode Models")
    print("="*60)
    
    kdtree_baseline = {8: 100.0, 16: 56.2, 32: 43.8, 64: 92.2, 128: 16.4}
    results = {}
    
    for nc in [16, 32, 64, 128]:
        print(f"\n--- Training {nc}-color model ---")
        palette = generate_jabcode_palette(nc)
        
        # Generate training data
        X_train, y_train = generate_training_data(palette, samples_per_color=500)
        split_idx = int(len(X_train) * 0.8)
        X_val, y_val = X_train[split_idx:], y_train[split_idx:]
        X_train, y_train = X_train[:split_idx], y_train[:split_idx]
        
        # Build model for this color count
        model = build_model(num_classes=nc)
        
        # Train
        model.fit(
            X_train, y_train,
            validation_data=(X_val, y_val),
            epochs=30,
            batch_size=256,
            verbose=0
        )
        
        # Evaluate
        X_test, y_test = generate_training_data(palette, samples_per_color=200)
        predictions = np.argmax(model.predict(X_test, verbose=0), axis=1)
        accuracy = np.mean(predictions == y_test) * 100
        results[nc] = accuracy
        
        # Export to TFLite
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        tflite_model = converter.convert()
        
        tflite_path = f"jabcode_{nc}color_classifier.tflite"
        with open(tflite_path, 'wb') as f:
            f.write(tflite_model)
        
        print(f"  Accuracy: {accuracy:.1f}% (K-d tree: {kdtree_baseline[nc]:.1f}%)")
        print(f"  Model: {tflite_path} ({len(tflite_model)/1024:.1f} KB)")
    
    print("\n" + "="*60)
    print("Per-Mode Model Comparison")
    print("="*60)
    print(f"{'Mode':<12} {'K-d Tree':<12} {'TensorFlow':<12} {'Improvement':<12}")
    print("-"*48)
    for nc in [16, 32, 64, 128]:
        kd = kdtree_baseline[nc]
        tf_acc = results[nc]
        improvement = tf_acc - kd
        print(f"{nc}-color    {kd:>8.1f}%    {tf_acc:>8.1f}%    {improvement:>+8.1f}%")
    
    return results


def main():
    print("="*60)
    print("TensorFlow Color Classifier POC for JABCode")
    print("="*60)
    
    # Train on 128-color palette (hardest case)
    print("\nGenerating 128-color palette...")
    palette_128 = generate_jabcode_palette(128)
    print(f"  Palette shape: {palette_128.shape}")
    print(f"  R levels: {len(np.unique(palette_128[:, 0]))}")
    print(f"  G levels: {len(np.unique(palette_128[:, 1]))}")
    print(f"  B levels: {len(np.unique(palette_128[:, 2]))}")
    
    # Generate training data
    print("\nGenerating training data...")
    X_train, y_train = generate_training_data(palette_128, samples_per_color=500)
    print(f"  Training samples: {len(X_train)}")
    
    # Split train/val
    split_idx = int(len(X_train) * 0.8)
    X_val, y_val = X_train[split_idx:], y_train[split_idx:]
    X_train, y_train = X_train[:split_idx], y_train[:split_idx]
    
    # Build and train model
    print("\nBuilding model...")
    model = build_model(num_classes=128)
    model.summary()
    
    print("\nTraining...")
    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=30,
        batch_size=256,
        verbose=1
    )
    
    # Evaluate across all color modes
    results = evaluate_by_color_mode(model)
    
    # Compare with K-d tree baseline
    print("\n" + "="*60)
    print("Comparison with K-d Tree Baseline (from C tests)")
    print("="*60)
    kdtree_baseline = {8: 100.0, 16: 56.2, 32: 43.8, 64: 92.2, 128: 16.4}
    
    print(f"{'Mode':<12} {'K-d Tree':<12} {'TensorFlow':<12} {'Improvement':<12}")
    print("-"*48)
    for nc in [8, 16, 32, 64, 128]:
        kd = kdtree_baseline[nc]
        tf_acc = results[nc]
        improvement = tf_acc - kd
        print(f"{nc}-color    {kd:>8.1f}%    {tf_acc:>8.1f}%    {improvement:>+8.1f}%")
    
    # Export to TFLite
    print("\n" + "="*60)
    print("Exporting to TensorFlow Lite...")
    print("="*60)
    
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    
    tflite_path = "jabcode_color_classifier.tflite"
    with open(tflite_path, 'wb') as f:
        f.write(tflite_model)
    
    print(f"  Model saved to: {tflite_path}")
    print(f"  Model size: {len(tflite_model) / 1024:.1f} KB")
    
    # Plot training history
    plt.figure(figsize=(12, 4))
    
    plt.subplot(1, 2, 1)
    plt.plot(history.history['loss'], label='Train Loss')
    plt.plot(history.history['val_loss'], label='Val Loss')
    plt.xlabel('Epoch')
    plt.ylabel('Loss')
    plt.legend()
    plt.title('Training Loss')
    
    plt.subplot(1, 2, 2)
    plt.plot(history.history['accuracy'], label='Train Acc')
    plt.plot(history.history['val_accuracy'], label='Val Acc')
    plt.xlabel('Epoch')
    plt.ylabel('Accuracy')
    plt.legend()
    plt.title('Training Accuracy')
    
    plt.tight_layout()
    plt.savefig('training_history.png', dpi=150)
    print("  Training plot saved to: training_history.png")
    
    # Train per-mode models for proper comparison
    train_per_mode_models()
    
    print("\n" + "="*60)
    print("POC Complete!")
    print("="*60)


if __name__ == "__main__":
    main()
