import streamlit as st
import pandas as pd
import plotly.express as px
import plotly.graph_objects as go
import os

# --- 1. CONFIGURATION ---
st.set_page_config(
    page_title="Hybrid Optimization Results",

    layout="wide"
)

# Custom CSS for "Stat Cards"
st.markdown("""
<style>
    .stat-card {
        background-color: #ffffff;
        padding: 20px;
        border-radius: 10px;
        border: 1px solid #e0e0e0;
        box-shadow: 0 2px 4px rgba(0,0,0,0.05);
        text-align: center;
    }
    .stat-value { font-size: 28px; font-weight: bold; color: #2ecc71; }
    .stat-label { font-size: 14px; color: #666; text-transform: uppercase; }
    .stat-diff { font-size: 12px; font-weight: bold; }
    .diff-pos { color: #2ecc71; }
    .diff-neg { color: #e74c3c; }
</style>
""", unsafe_allow_html=True)

CSV_FILE = "results.csv"

# --- 2. DATA LOADING ---
@st.cache_data
def load_data():
    if not os.path.exists(CSV_FILE):
        return None
    df = pd.read_csv(CSV_FILE)

    # Clean numeric columns (handle 'FAILED' text)
    cols_to_clean = ['BestFitness', 'Power', 'Load', 'Network', 'Link']
    for col in cols_to_clean:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors='coerce')
    return df

df = load_data()

# --- 3. MAIN DASHBOARD ---
if df is None or df.empty:
    st.error("Results file not found. Please run your Java simulation first!")
    st.stop()

# Sidebar
st.sidebar.title("Analysis Controls")
all_hosts = sorted(df['HostCount'].unique())
selected_hosts = st.sidebar.multiselect("Select Scenarios (Hosts):", all_hosts, default=all_hosts)

if not selected_hosts:
    st.warning("Select at least one scenario.")
    st.stop()

# Filter Data
filtered_df = df[df['HostCount'].isin(selected_hosts)]

# --- SECTION 1: THE VERDICT (Executive Summary) ---
st.title("Hybrid Algorithm Performance Report")
st.markdown("### Executive Summary")

# Calculate Averages for the selected scenarios
avg_stats = filtered_df.groupby('Algorithm')[['BestFitness', 'Power', 'Load', 'Network', 'Link']].mean()

if 'Hybrid ACO-PSO' in avg_stats.index:
    hybrid_fit = avg_stats.loc['Hybrid ACO-PSO', 'BestFitness']
    hybrid_pwr = avg_stats.loc['Hybrid ACO-PSO', 'Power']

    # Compare against ACO (the reliable competitor)
    competitor = 'ACO' if 'ACO' in avg_stats.index else None

    if competitor:
        comp_fit = avg_stats.loc[competitor, 'BestFitness']
        comp_pwr = avg_stats.loc[competitor, 'Power']

        # Calculate improvements (Negative change is GOOD for fitness/power)
        fit_imp = ((comp_fit - hybrid_fit) / comp_fit) * 100
        pwr_imp = ((comp_pwr - hybrid_pwr) / comp_pwr) * 100

        col1, col2, col3 = st.columns(3)
        with col1:
            st.markdown(f"""
            <div class="stat-card">
                <div class="stat-label">Optimization Quality</div>
                <div class="stat-value">{hybrid_fit:,.0f}</div>
                <div class="stat-diff diff-pos">▼ {fit_imp:.1f}% Better than {competitor}</div>
            </div>
            """, unsafe_allow_html=True)
        with col2:
            st.markdown(f"""
            <div class="stat-card">
                <div class="stat-label">Energy Efficiency</div>
                <div class="stat-value">{hybrid_pwr:,.0f} W</div>
                <div class="stat-diff diff-pos">▼ {pwr_imp:.1f}% Power Saved vs {competitor}</div>
            </div>
            """, unsafe_allow_html=True)
        with col3:
            success_rate = (filtered_df[filtered_df['Algorithm'] == 'Hybrid ACO-PSO']['BestFitness'].notna().sum() /
                            len(filtered_df[filtered_df['Algorithm'] == 'Hybrid ACO-PSO'])) * 100
            st.markdown(f"""
            <div class="stat-card">
                <div class="stat-label">Hybrid Reliability</div>
                <div class="stat-value">{success_rate:.0f}%</div>
                <div class="stat-diff">Success Rate on Selected Scenarios</div>
            </div>
            """, unsafe_allow_html=True)

# --- SECTION 2: VISUAL PROOF ---
st.markdown("---")
st.subheader("Performance Analysis")

tab1, tab2 = st.tabs(["Fitness & Power ", "Trade-off Details "])

with tab1:
    st.caption("Lower bars are better.")
    metric = st.radio("Select Metric:", ["BestFitness", "Power"], horizontal=True)

    # Prepare Data
    chart_df = filtered_df.groupby(['HostCount', 'Algorithm'])[metric].mean().reset_index()

    # Explicit Colors: Hybrid is Green (Good), Others neutral/alert
    color_map = {
        'Hybrid ACO-PSO': '#2ecc71', # Green
        'ACO': '#3498db',          # Blue
        'PSO': '#e74c3c'           # Red
    }

    fig = px.bar(
        chart_df, x="HostCount", y=metric, color="Algorithm", barmode="group",
        color_discrete_map=color_map, text_auto='.2s',
        title=f"Average {metric} by Scenario Difficulty"
    )
    fig.update_layout(yaxis_title=metric, xaxis_title="Hosts (Scenario Difficulty)", plot_bgcolor="white")
    st.plotly_chart(fig, use_container_width=True)

with tab2:
    st.write("**Why does the Hybrid win?**")
    st.write("The Hybrid algorithm intelligently sacrifices a small amount of Load Balancing to achieve massive Power Savings.")

    # Side-by-side comparison of Load vs Power
    col_a, col_b = st.columns(2)

    # Power Chart
    power_df = filtered_df.groupby(['HostCount', 'Algorithm'])['Power'].mean().reset_index()
    fig_p = px.line(power_df, x="HostCount", y="Power", color="Algorithm", markers=True,
                    color_discrete_map=color_map, title="1. Power Consumption (Lower is Better)")
    col_a.plotly_chart(fig_p, use_container_width=True)

    # Load Chart
    load_df = filtered_df.groupby(['HostCount', 'Algorithm'])['Load'].mean().reset_index()
    fig_l = px.line(load_df, x="HostCount", y="Load", color="Algorithm", markers=True,
                    color_discrete_map=color_map, title="2. Load Imbalance (Lower is Better)")
    col_b.plotly_chart(fig_l, use_container_width=True)

    st.info("Notice in Chart 1 (Power), the **Green Line (Hybrid)** is much lower than Blue (ACO). "
            "In Chart 2 (Load), the Green line is slightly higher. "
            "This proves the Hybrid found a **smarter trade-off** to minimize the total Fitness score.")

# --- SECTION 3: RAW DATA ---
st.markdown("---")
with st.expander("View Raw Data Table"):
    st.dataframe(
        filtered_df.style.format({
            'BestFitness': '{:,.0f}',
            'Power': '{:,.2f}',
            'Load': '{:.4f}',
            'Network': '{:,.0f}',
            'Link': '{:.4f}'
        }),
        use_container_width=True
    )

# --- SECTION 5: CLOUD INFRASTRUCTURE MONITORING ---
st.markdown("---")
st.subheader("Live Cloud Infrastructure (AWS)")
st.markdown("Real-time monitoring of the EC2 instance running the simulation.")

GRAFANA_URL = "http://13.48.42.249:3000/public-dashboards/84775fb8902b4b829d54f404940b293c&kiosk"

st.components.v1.iframe(src=GRAFANA_URL, height=600, scrolling=True)