SELECT customers.name,order_date,sku,quan
FROM customers,orders,items
WHERE customers.cid = orders.cid
AND orders.oid = items.oid
ORDER BY sku, order_date
