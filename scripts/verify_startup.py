import requests
import time
import sys

# Configuration
BASE_URL = "http://localhost:8090/api"
USER1_AUTH = "Basic user1:pass" # Buyer
USER2_AUTH = "Basic user2:pass" # Seller

def log(msg):
    print(f"[TEST] {msg}")

def fail(msg):
    print(f"[FAIL] {msg}")
    sys.exit(1)

def get_asset(auth, asset_type):
    headers = {"Authorization": auth}
    resp = requests.get(f"{BASE_URL}/asset/get", params={"type": asset_type}, headers=headers)
    if resp.status_code != 200:
        return 0.0
    data = resp.json().get("data")
    if not data:
        return 0.0
    return float(data.get("available", 0))

def recharge(auth, asset_type, amount):
    headers = {"Authorization": auth}
    resp = requests.get(f"{BASE_URL}/asset/recharge", params={"type": asset_type, "amount": amount}, headers=headers)
    assert resp.status_code == 200, f"Recharge failed: {resp.text}"

def place_order(auth, side, price, amount):
    headers = {"Authorization": auth}
    endpoint = f"{BASE_URL}/trade/{side.lower()}"
    resp = requests.get(endpoint, params={"price": price, "amount": amount}, headers=headers)
    assert resp.status_code == 200, f"Place order failed: {resp.text}"
    return resp.json()["data"]["id"]

def get_order_status(auth, order_id):
    headers = {"Authorization": auth}
    resp = requests.get(f"{BASE_URL}/order/get", params={"order_id": order_id}, headers=headers)
    return resp.json()["data"]

def run_sweep_test():
    log("Starting Sweep (Multi-Order) Test...")
    
    # --- 1. Initialize Assets ---
    log("Initializing Assets...")
    # Buyer needs enough cash for worst case: 12 * 150 = 1800
    if get_asset(USER1_AUTH, "USD") < 2000:
        recharge(USER1_AUTH, "USD", 2000)
    
    # Seller needs 20 APPL
    if get_asset(USER2_AUTH, "APPL") < 20:
        recharge(USER2_AUTH, "APPL", 20)

    u1_usd_start = get_asset(USER1_AUTH, "USD")
    u1_appl_start = get_asset(USER1_AUTH, "APPL")
    u2_usd_start = get_asset(USER2_AUTH, "USD")
    u2_appl_start = get_asset(USER2_AUTH, "APPL")
    
    log(f"Start State -> Buyer USD: {u1_usd_start}, APPL: {u1_appl_start} | Seller USD: {u2_usd_start}, APPL: {u2_appl_start}")

    # --- 2. Place Maker Orders (Seller) ---
    # Order A: Sell 10 @ 100
    log("User2 placing SELL order A: 10 APPL @ $100")
    sell_id_1 = place_order(USER2_AUTH, "SELL", 100, 10)
    
    # Order B: Sell 10 @ 150
    log("User2 placing SELL order B: 10 APPL @ $150")
    sell_id_2 = place_order(USER2_AUTH, "SELL", 150, 10)
    
    time.sleep(0.5) 

    # --- 3. Place Taker Order (Buyer) ---
    # Buy 12 @ 150. Expect to sweep Order A (10) and part of Order B (2).
    log("User1 placing BUY order: 12 APPL @ $150 (Sweep)")
    buy_id = place_order(USER1_AUTH, "BUY", 150, 12)

    # --- 4. Wait for Matching ---
    log("Waiting for matching engine...")
    time.sleep(2) 

    # --- 5. Verify Orders ---
    s1_info = get_order_status(USER2_AUTH, sell_id_1)
    s2_info = get_order_status(USER2_AUTH, sell_id_2)
    b_info = get_order_status(USER1_AUTH, buy_id)

    log(f"Order A Status: {s1_info['status']} (Expected: FINISHED)")
    log(f"Order B Status: {s2_info['status']} (Expected: TRADING/PARTIAL)")
    log(f"Buy Order Status: {b_info['status']} (Expected: FINISHED)")

    if s1_info['status'] != "FINISHED": fail("Order A should be FINISHED")
    if b_info['status'] != "FINISHED": fail("Buy Order should be FINISHED")
    # Note: Depending on your engine impl, partially filled might be TRADING or PARTIAL_FILLED
    if s2_info['status'] not in ["TRADING", "PARTIAL_FILLED"]: fail(f"Order B should be TRADING, got {s2_info['status']}")

    # Verify filled amounts
    if float(s2_info['finishedAmount']) != 2.0:
        fail(f"Order B finished amount mismatch. Expected 2.0, got {s2_info['finishedAmount']}")

    # --- 6. Verify Assets (Crucial for correct clearing logic) ---
    u1_usd_end = get_asset(USER1_AUTH, "USD")
    u1_appl_end = get_asset(USER1_AUTH, "APPL")
    u2_usd_end = get_asset(USER2_AUTH, "USD")
    u2_appl_end = get_asset(USER2_AUTH, "APPL")

    # Cost Calculation:
    # 10 * 100 = 1000
    # 2 * 150 = 300
    # Total Cost = 1300
    expected_cost = 1300.0
    
    log(f"End State -> Buyer USD: {u1_usd_end}, APPL: {u1_appl_end}")
    
    # Check Buyer USD (Spent 1300)
    if u1_usd_end != u1_usd_start - expected_cost:
        fail(f"Buyer USD mismatch. Expected -{expected_cost}, Actual delta: {u1_usd_end - u1_usd_start}")
        
    # Check Buyer APPL (Gained 12)
    if u1_appl_end != u1_appl_start + 12:
         fail(f"Buyer APPL mismatch. Expected +12, Actual delta: {u1_appl_end - u1_appl_start}")

    # Check Seller USD (Gained 1300)
    if u2_usd_end != u2_usd_start + expected_cost:
        fail(f"Seller USD mismatch. Expected +{expected_cost}, Actual delta: {u2_usd_end - u2_usd_start}")

    log("Sweep Test PASSED! Logic Verified.")

if __name__ == "__main__":
    try:
        run_sweep_test()
    except Exception as e:
        fail(f"Script crashed: {e}")