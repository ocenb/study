import json
import xml.etree.ElementTree as ET


def json_to_xml(json_obj, line_padding=""):
    result_list = []

    for key, value in json_obj.items():
        if isinstance(value, dict):
            result_list.append(f"{line_padding}<{key}>")
            result_list.append(json_to_xml(value, line_padding + "  "))
            result_list.append(f"{line_padding}</{key}>")
        elif isinstance(value, list):
            for sub_value in value:
                result_list.append(f"{line_padding}<{key}>")
                result_list.append(json_to_xml(sub_value, line_padding + "  "))
                result_list.append(f"{line_padding}</{key}>")
        else:
            result_list.append(f"{line_padding}<{key}>{value}</{key}>")

    return "\n".join(result_list)


with open("input.json", "r", encoding="utf-8") as json_file:
    json_data = json.load(json_file)

xml_result = json_to_xml(json_data)
with open("output.xml", "w", encoding="utf-8") as xml_file:
    xml_file.write(f"<root>\n{xml_result}\n</root>")
