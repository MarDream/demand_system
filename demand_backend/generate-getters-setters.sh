#!/bin/bash
# 为指定的Java类生成getter/setter方法，替换Lombok @Data注解

generate_methods() {
    local file=$1
    echo "处理文件: $file"

    # 提取类中的字段
    fields=$(grep -E "^\s+private\s+" "$file" | grep -v "static final")

    if [ -z "$fields" ]; then
        echo "  未找到字段"
        return
    fi

    # 生成getter/setter
    methods=""
    while IFS= read -r field; do
        # 解析字段类型和名称
        type=$(echo "$field" | sed -E 's/.*private\s+([^;]+)\s+.*/\1/' | xargs)
        name=$(echo "$field" | sed -E 's/.*private\s+[^;]+\s+([^;=]+).*/\1/' | sed 's/;.*//' | xargs)

        # 首字母大写
        cap_name=$(echo "$name" | sed 's/^\(.\)/\U\1/')

        # 生成getter
        methods="$methods
    public $type get$cap_name() {
        return $name;
    }
"

        # 生成setter
        methods="$methods
    public void set$cap_name($type $name) {
        this.$name = $name;
    }
"
    done <<< "$fields"

    # 在类的最后一个}之前插入方法
    sed -i "s/^}$/$methods\n}/" "$file"

    # 移除@Data注解
    sed -i '/@Data/d' "$file"

    echo "  已生成 getter/setter"
}

# 查找所有使用@Data的Java文件
find src/main/java -name "*.java" -type f | while read -r file; do
    if grep -q "@Data" "$file"; then
        generate_methods "$file"
    fi
done

echo "完成！"
