import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import os

# --- Configuration ---
CSV_FILENAME = "results.csv"
OUTPUT_FILENAME = "minimalist_dashboard_gaps.png"

# Clean, High-Contrast Colors
ALGO_COLORS = {
    'ACO': '#2E86AB',      # Strong Blue
    'PSO': '#F24236',      # Red/Orange
    'Hybrid ACO-PSO': '#56A902' # Sharp Green
}
DEFAULT_COLORS = ['#2E86AB', '#F24236', '#56A902', '#5D576B', '#ED6A5A']

metrics_to_plot = [
    {'col': 'BestFitness', 'title': 'Best Fitness Score', 'ylabel': 'Score (Lower is Better)'},
    {'col': 'Power', 'title': 'Power Consumption', 'ylabel': 'Watts'},
    {'col': 'Load', 'title': 'Load Imbalance', 'ylabel': 'Standard Deviation'},
    {'col': 'Network', 'title': 'Total Network Traffic', 'ylabel': 'Traffic Cost'},
    {'col': 'Link', 'title': 'Max Link Utilization', 'ylabel': 'Utilization %'}
]

def setup_style():
    """Sets a clean, modern style."""
    try:
        # Try modern seaborn style if available
        plt.style.use('seaborn-v0_8-white')
    except:
        plt.style.use('classic')

    plt.rcParams['font.family'] = 'sans-serif'
    plt.rcParams['font.size'] = 12
    plt.rcParams['axes.linewidth'] = 0.8
    plt.rcParams['axes.edgecolor'] = '#333333'

def create_minimal_dashboard(df):
    # Create figure with constrained_layout for perfect spacing
    fig, axes = plt.subplots(3, 2, figsize=(16, 15), constrained_layout=True)
    axes = axes.flatten()

    # Get data for X-axis
    algorithms = sorted(df['Algorithm'].unique())
    host_counts = sorted(df['HostCount'].unique())
    x = np.arange(len(host_counts))

    # --- GAP CALCULATION ---
    total_group_width = 0.85             # The group of bars takes up 85% of the space between ticks
    slot_width = total_group_width / len(algorithms) # Width allocated for one bar + its gap
    bar_width = slot_width * 0.85        # The actual bar is 85% of its slot (creates the gap)

    # Loop through metrics
    for i, metric in enumerate(metrics_to_plot):
        ax = axes[i]
        col_name = metric['col']

        if col_name not in df.columns:
            continue

        # Prepare data
        avg_data = df.groupby(['HostCount', 'Algorithm'])[col_name].mean().reset_index()
        pivot_data = avg_data.pivot(index='HostCount', columns='Algorithm', values=col_name)

        # Plot Bars
        for j, algo in enumerate(algorithms):
            if algo in pivot_data:
                # Calculate center position based on the SLOT width
                offset = (j - len(algorithms) / 2) * slot_width + (slot_width / 2)
                color = ALGO_COLORS.get(algo, DEFAULT_COLORS[j % len(DEFAULT_COLORS)])

                vals = pivot_data[algo]
                # Use the narrower BAR width for plotting
                bars = ax.bar(x + offset, vals, bar_width, label=algo, color=color, alpha=0.9, zorder=3)

                # Labels: Integer for big numbers, Float for small ratios
                if col_name in ['Load', 'Link']:
                    labels = [f'{v:.2f}' if not np.isnan(v) and v > 0 else '' for v in vals]
                else:
                    labels = [f'{v:.0f}' if not np.isnan(v) and v > 0 else '' for v in vals]

                ax.bar_label(bars, labels=labels, padding=3, fontsize=10, rotation=0)

        # Clean Styling per Subplot
        ax.set_title(metric['title'], fontsize=14, fontweight='bold', pad=10)
        ax.set_ylabel(metric['ylabel'], fontsize=11, color='#555555')
        ax.set_xticks(x)
        ax.set_xticklabels(host_counts, fontsize=10)

        # Subtle grid behind bars
        ax.grid(axis='y', color='#e0e0e0', linestyle='-', linewidth=0.8, zorder=0)

        # Remove top and right borders
        ax.spines['top'].set_visible(False)
        ax.spines['right'].set_visible(False)
        ax.spines['left'].set_color('#888888')
        ax.spines['bottom'].set_color('#888888')

    # Hide the empty 6th slot completely
    axes[-1].axis('off')

    # Global Legend at the top
    handles, labels = axes[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc='upper center', bbox_to_anchor=(0.5, 1.02),
               ncol=3, frameon=False, fontsize=14)

    # Shared X-Axis Label
    fig.text(0.5, -0.01, 'Scenario Difficulty (Number of Hosts)', ha='center', fontsize=14, fontweight='bold', color='#333333')

    # Save
    plt.savefig(OUTPUT_FILENAME, dpi=300, bbox_inches='tight')
    print(f"Success! Integer-rounded dashboard with gaps saved as: {OUTPUT_FILENAME}")
    plt.close()

if __name__ == "__main__":
    setup_style()
    if not os.path.exists(CSV_FILENAME):
        print(f"Error: {CSV_FILENAME} not found!")
        exit()

    try:
        df = pd.read_csv(CSV_FILENAME)
        # Clean "FAILED" strings to NaN
        for metric in metrics_to_plot:
            col = metric['col']
            if col in df.columns:
                df[col] = pd.to_numeric(df[col], errors='coerce')

        create_minimal_dashboard(df)
    except Exception as e:
        print(f"An error occurred: {e}")