"""Lista os testes que falharam a partir dos relatórios JUnit (XML) do Gradle.

Uso: python failed_tests.py <pasta-raiz> — imprime uma linha por teste: `módulo · Classe.teste`.
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def failed_tests(root: Path) -> list[str]:
    found = []
    for report in sorted(root.glob("**/build/test-results/**/*.xml")):
        module = report.parts[len(root.parts)] if len(report.parts) > len(root.parts) + 1 else "?"
        try:
            tree = ET.parse(report)
        except ET.ParseError:
            found.append(f"{module} · relatório ilegível: {report.name}")
            continue
        for case in tree.iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                found.append(f"{module} · {case.get('classname', '?')}.{case.get('name', '?')}")
    return found


if __name__ == "__main__":
    print("\n".join(failed_tests(Path(sys.argv[1]))))
