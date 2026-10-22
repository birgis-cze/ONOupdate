import requests
import sys

URL = "https://m.tank-ono.cz/cz/index.php?page=cenik"

def check_web():
    print(f"Stahuji {URL}...")
    try:
        # Přidáme User-Agent, aby nás server neblokoval
        headers = {'User-Agent': 'Mozilla/5.0 (Android) TankOnoWidget/1.0'}
        response = requests.get(URL, headers=headers, timeout=15)
        
        # Pokud server vrátí chybu (404, 500 atd.)
        if response.status_code != 200:
            print(f"CHYBA: Server vrátil HTTP {response.status_code}")
            sys.exit(1)
            
        html = response.text
        
        # Tady kontrolujeme, jestli tam jsou tagy, které hledá tvůj TankOnoScraper.kt
        if "divrow2" not in html or "divprice" not in html:
            print("CHYBA: Nenašel jsem očekávané HTML třídy ('divrow2' nebo 'divprice').")
            print("Pravděpodobně se změnila struktura webu Tank ONO!")
            sys.exit(1)
            
        # Zkusíme zhruba spočítat, kolik cen tam je
        price_count = html.count("divprice")
        if price_count == 0:
            print("CHYBA: Nalezeno 0 cenových elementů.")
            sys.exit(1)
            
        print(f"ÚSPĚCH: Vše vypadá dobře. Nalezeno {price_count} cenových elementů.")
        
    except Exception as e:
        print(f"CHYBA při stahování: {e}")
        sys.exit(1)

if __name__ == "__main__":
    check_web()