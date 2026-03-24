using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Gastapp_API.Migrations
{
    public partial class AddIsDefaultCategoryFlag : Migration
    {
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<bool>(
                name: "IsDefaultCategory",
                table: "Categories",
                type: "boolean",
                nullable: false,
                defaultValue: false);

            migrationBuilder.Sql(@"
                UPDATE ""Categories""
                SET ""IsDefaultCategory"" = TRUE,
                    ""CategoryName"" = 'Sin categoria'
                WHERE UPPER(""CategoryName"") = 'SIN CATEGORIA'
                   OR UPPER(""CategoryName"") = 'SIN CATEGORÍA';
            ");
        }

        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "IsDefaultCategory",
                table: "Categories");
        }
    }
}
