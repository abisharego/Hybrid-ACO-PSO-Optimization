import pandas as pd
import matplotlib.pyplot as plt
import numpy as np # Needed for handling potential NaN values

# --- Configuration ---
CSV_FILENAME = "results.csv"
FITNESS_PLOT_FILENAME = "fitness_comparison.png"
TIME_PLOT_FILENAME = "time_comparison.png"
FAILED_VALUE_REPLACEMENT = np.nan # Treat 'FAILED' as Not a Number for calculations

# --- 1. Load the Data ---
try:
    df = pd.read_csv(CSV_FILENAME)
    print(f"Successfully loaded data from {CSV_FILENAME}")
except FileNotFoundError:
    print(f"Error: Could not find the file {CSV_FILENAME}.")
    print("Please make sure the CSV file is in the same directory as the script.")
    exit()
except Exception as e:
    print(f"An error occurred while reading the CSV file: {e}")
    exit()

# --- 2. Preprocess Data ---
# Convert 'FAILED' fitness strings to NaN (Not a Number)
# We use pd.to_numeric with errors='coerce' which turns unparsable strings into NaN
df['BestFitness'] = pd.to_numeric(df['BestFitness'], errors='coerce')

# Optional: Replace NaN with a very large number if you want to show failures visually
# instead of excluding them from averages. Using NaN excludes them.
# df['BestFitness'].fillna(2 * df['BestFitness'].max(), inplace=True) # Example replacement

print("\nPreprocessing complete. 'FAILED' fitness values treated as NaN.")

# --- 3. Calculate Averages ---
# Group by HostCount and Algorithm, then calculate the mean for Fitness and Time
# NaN values are automatically skipped by the .mean() function
average_results = df.groupby(['HostCount', 'Algorithm']).agg(
    AvgFitness=('BestFitness', 'mean'),
    AvgTime=('TimeMs', 'mean')
).reset_index() # reset_index turns the grouped indices back into columns

print("\nCalculated average results:")
print(average_results)

# --- 4. Prepare for Plotting ---
# Pivot the table to get Algorithms as columns, suitable for grouped bar charts
fitness_pivot = average_results.pivot(index='HostCount', columns='Algorithm', values='AvgFitness')
time_pivot = average_results.pivot(index='HostCount', columns='Algorithm', values='AvgTime')

# Get unique algorithms and host counts for plotting labels and positions
algorithms = df['Algorithm'].unique()
host_counts = df['HostCount'].unique()
host_counts.sort() # Ensure host counts are in order

# --- 5. Create Fitness Plot ---
fig_fit, ax_fit = plt.subplots(figsize=(12, 7)) # Create a figure and an axes object
num_algorithms = len(algorithms)
bar_width = 0.25 # Adjust as needed based on the number of algorithms
index = np.arange(len(host_counts)) # the label locations

# Plot bars for each algorithm
for i, algo in enumerate(algorithms):
    # Calculate offset for each bar group
    offset = (i - num_algorithms / 2 + 0.5) * bar_width
    bars = ax_fit.bar(index + offset, fitness_pivot[algo], bar_width, label=algo)

    # Optional: Add text labels on top of bars
    # ax_fit.bar_label(bars, fmt='%.1f', padding=3, rotation=90, fontsize=8)


# Add labels, title, and legend
ax_fit.set_xlabel("Number of Hosts")
ax_fit.set_ylabel("Average Best Fitness (Lower is Better)")
ax_fit.set_title("Average Fitness Comparison by Number of Hosts")
ax_fit.set_xticks(index)
ax_fit.set_xticklabels(host_counts)
ax_fit.legend(title="Algorithm")
ax_fit.grid(axis='y', linestyle='--', alpha=0.7) # Add horizontal grid lines

plt.tight_layout() # Adjust layout to prevent labels overlapping
plt.savefig(FITNESS_PLOT_FILENAME) # Save the plot as an image file
print(f"\nFitness plot saved as {FITNESS_PLOT_FILENAME}")
# plt.show() # Uncomment to display the plot directly

# --- 6. Create Time Plot ---
fig_time, ax_time = plt.subplots(figsize=(12, 7))

# Plot bars for each algorithm
for i, algo in enumerate(algorithms):
    offset = (i - num_algorithms / 2 + 0.5) * bar_width
    bars = ax_time.bar(index + offset, time_pivot[algo], bar_width, label=algo)
    # ax_time.bar_label(bars, fmt='%d', padding=3, rotation=90, fontsize=8) # Integer format for time

# Add labels, title, and legend
ax_time.set_xlabel("Number of Hosts")
ax_time.set_ylabel("Average Execution Time (ms)")
ax_time.set_title("Average Execution Time Comparison by Number of Hosts")
ax_time.set_xticks(index)
ax_time.set_xticklabels(host_counts)
ax_time.legend(title="Algorithm")
ax_time.grid(axis='y', linestyle='--', alpha=0.7)

plt.tight_layout()
plt.savefig(TIME_PLOT_FILENAME)
print(f"Time plot saved as {TIME_PLOT_FILENAME}")
# plt.show() # Uncomment to display the plot directly

print("\nPlotting complete.")