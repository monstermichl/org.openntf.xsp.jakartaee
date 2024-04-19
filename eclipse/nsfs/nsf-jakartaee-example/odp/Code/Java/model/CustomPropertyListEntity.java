package model;

import java.util.List;

import org.openntf.xsp.nosql.mapping.extension.DominoRepository;
import org.openntf.xsp.nosql.mapping.extension.ItemStorage;

import jakarta.nosql.mapping.Column;
import jakarta.nosql.mapping.Entity;
import jakarta.nosql.mapping.Id;

@Entity("CustomPropertyListEntity")
public class CustomPropertyListEntity {
	public interface Repository extends DominoRepository<CustomPropertyListEntity, String> {
		
	}
	
	@Id
	private String id;
	
	@Column("CustomPropertyList")
	@ItemStorage(type=ItemStorage.Type.JSON)
	List<CustomPropertyType> customPropertyList;
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	
	public List<CustomPropertyType> getCustomPropertyList() {
		return customPropertyList;
	}
	public void setCustomPropertyList(List<CustomPropertyType> customPropertyList) {
		this.customPropertyList = customPropertyList;
	}
}
